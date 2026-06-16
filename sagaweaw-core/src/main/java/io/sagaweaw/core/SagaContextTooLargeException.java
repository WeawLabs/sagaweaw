package io.sagaweaw.core;

public class SagaContextTooLargeException extends RuntimeException {

    private final String sagaName;
    private final int maxBytes;
    private final int actualBytes;

    public SagaContextTooLargeException(String sagaName, int maxBytes, int actualBytes) {
        super("Saga context for '%s' exceeds limit of %d bytes (%d actual). "
                .formatted(sagaName, maxBytes, actualBytes)
                + "Annotate large fields with @SagaMask or fetch data in-step instead of storing in context.");
        this.sagaName = sagaName;
        this.maxBytes = maxBytes;
        this.actualBytes = actualBytes;
    }

    public String getSagaName()  { return sagaName;  }
    public int    getMaxBytes()  { return maxBytes;   }
    public int    getActualBytes() { return actualBytes; }
}
