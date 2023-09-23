package zone.rong.rongasm.api;

import speiger.src.collections.objects.maps.impl.hash.Object2ObjectOpenHashMap;

public class ResourceCache extends Object2ObjectOpenHashMap<String, byte[]> {

    public byte[] add(String s, byte[] bytes) {
        return super.put(s, bytes);
    }

    @Override
    public byte[] put(String s, byte[] bytes) {
        return bytes;
    }
}
