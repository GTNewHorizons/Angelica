package net.coderbot.iris.pipeline.transform.parameter;

public interface JobParameters {

    JobParameters EMPTY = new JobParameters() {
        @Override
        public boolean equals(Object other) {
            return other == this;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    };

    boolean equals(Object other);

    int hashCode();
}
