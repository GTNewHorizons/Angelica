#include <algorithm>
#include <atomic>
#include <chrono>
#include <cinttypes>
#include <cstdint>
#include <cstdio>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

#if defined(_WIN32)
#include <windows.h>
#else
#include <unistd.h>
#endif

#include "TracyFileWrite.hpp"
#include "TracySysUtil.hpp"
#include "TracyWorker.hpp"

#if defined(_WIN32)
#define ANG_CAP_API __declspec(dllexport)
#else
#define ANG_CAP_API __attribute__((visibility("default")))
#endif

namespace {

enum CapState : int {
    STATE_IDLE = 0,
    STATE_CONNECTING = 1,
    STATE_RECORDING = 2,
    STATE_SAVING = 3,
    STATE_DONE = 4,
    STATE_FAILED = 5,
};

std::atomic<int> g_state{ STATE_IDLE };
std::atomic<bool> g_stopRequested{ false };
std::atomic<int64_t> g_recordStartNs{ 0 };
std::atomic<int64_t> g_frozenElapsedMs{ -1 };
std::mutex g_mutex;
std::thread g_thread;
std::string g_error;
std::string g_errorSnapshot;

struct ThreadJoiner {
    ~ThreadJoiner() {
        g_stopRequested.store(true, std::memory_order_release);
        if (g_thread.joinable()) {
            g_thread.join();
        }
    }
};
ThreadJoiner g_threadJoiner;

uint64_t CurrentPid() {
#if defined(_WIN32)
    return (uint64_t)GetCurrentProcessId();
#else
    return (uint64_t)getpid();
#endif
}

void SetError(const std::string& msg) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_error = msg;
}

void RunCapture(std::string path, int port, int seconds, int memPercent) {
    try {
        int64_t memLimit = -1;
        if (memPercent > 0) {
            const int64_t clamped = std::clamp<int64_t>(memPercent, 1, 999);
            memLimit = clamped * (int64_t)tracy::GetPhysicalMemorySize() / 100;
        }

        tracy::Worker worker("127.0.0.1", (uint16_t)port, memLimit);

        const auto waitStart = std::chrono::steady_clock::now();
        while (!worker.HasData()) {
            if (g_stopRequested.load(std::memory_order_acquire)) {
                SetError("capture cancelled before the client connected");
                g_state.store(STATE_FAILED, std::memory_order_release);
                return;
            }
            const auto handshake = worker.GetHandshakeStatus();
            if (handshake == tracy::HandshakeProtocolMismatch) {
                SetError("client uses an incompatible protocol version");
                g_state.store(STATE_FAILED, std::memory_order_release);
                return;
            }
            if (handshake == tracy::HandshakeNotAvailable) {
                SetError("client not available for connection");
                g_state.store(STATE_FAILED, std::memory_order_release);
                return;
            }
            if (handshake == tracy::HandshakeDropped) {
                SetError("client disconnected during handshake");
                g_state.store(STATE_FAILED, std::memory_order_release);
                return;
            }
            if (std::chrono::steady_clock::now() - waitStart >= std::chrono::seconds(5)) {
                SetError("timed out connecting to the Tracy client; its port may be taken, set -Dangelica.tracy.port");
                g_state.store(STATE_FAILED, std::memory_order_release);
                return;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }

        const uint64_t pid = worker.GetPid();
        if (pid != CurrentPid()) {
            char buf[96];
            snprintf(buf, sizeof(buf), "attached to another process (pid %" PRIu64 ")", pid);
            SetError(buf);
            worker.Disconnect();
            g_state.store(STATE_FAILED, std::memory_order_release);
            return;
        }

        g_state.store(STATE_RECORDING, std::memory_order_release);
        const auto t0 = std::chrono::steady_clock::now();
        g_recordStartNs.store(t0.time_since_epoch().count(), std::memory_order_release);

        while (worker.IsConnected()) {
            const auto now = std::chrono::steady_clock::now();
            const bool timeUp = seconds > 0 && std::chrono::duration_cast<std::chrono::seconds>(now - t0).count() >= seconds;
            if (g_stopRequested.load(std::memory_order_acquire) || timeUp) {
                worker.Disconnect();
                while (worker.IsConnected()) std::this_thread::sleep_for(std::chrono::milliseconds(100));
                break;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }

        const auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - t0).count();
        g_frozenElapsedMs.store(elapsedMs, std::memory_order_release);

        g_state.store(STATE_SAVING, std::memory_order_release);
        std::unique_ptr<tracy::FileWrite> f(tracy::FileWrite::Open(path.c_str(), tracy::FileCompression::Zstd, 3, 4));
        if (!f) {
            SetError("cannot open output file");
            g_state.store(STATE_FAILED, std::memory_order_release);
            return;
        }
        worker.Write(*f, false);
        f->Finish();
        g_state.store(STATE_DONE, std::memory_order_release);
    } catch (const std::exception& e) {
        SetError(e.what());
        g_state.store(STATE_FAILED, std::memory_order_release);
    } catch (...) {
        SetError("unknown capture error");
        g_state.store(STATE_FAILED, std::memory_order_release);
    }
}

}

extern "C" {

ANG_CAP_API int ang_cap_start(const char* path, int port, int seconds, int memPercent) {
    std::lock_guard<std::mutex> lock(g_mutex);

    const int state = g_state.load(std::memory_order_acquire);
    if (state == STATE_CONNECTING || state == STATE_RECORDING || state == STATE_SAVING) {
        return 1;
    }
    if (g_thread.joinable()) {
        g_thread.join();
    }

    g_stopRequested.store(false, std::memory_order_release);
    g_recordStartNs.store(0, std::memory_order_release);
    g_frozenElapsedMs.store(-1, std::memory_order_release);
    g_error.clear();
    g_state.store(STATE_CONNECTING, std::memory_order_release);

    g_thread = std::thread(RunCapture, std::string(path ? path : ""), port, seconds, memPercent);
    return 0;
}

ANG_CAP_API void ang_cap_stop(void) {
    g_stopRequested.store(true, std::memory_order_release);
}

ANG_CAP_API int ang_cap_state(void) {
    return g_state.load(std::memory_order_acquire);
}

ANG_CAP_API int64_t ang_cap_elapsed_ms(void) {
    const int64_t frozen = g_frozenElapsedMs.load(std::memory_order_acquire);
    if (frozen >= 0) return frozen;
    if (g_state.load(std::memory_order_acquire) != STATE_RECORDING) return 0;
    const int64_t startNs = g_recordStartNs.load(std::memory_order_acquire);
    if (startNs == 0) return 0;
    const int64_t nowNs = std::chrono::steady_clock::now().time_since_epoch().count();
    return (nowNs - startNs) / 1000000;
}

ANG_CAP_API const char* ang_cap_error(void) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_errorSnapshot = g_error;
    return g_errorSnapshot.c_str();
}

}
