package cl.duoc.syncpass.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;
    private final Instant timestamp;

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .data(null)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    /** Error con detalle, como el {@code data.errores} que exige el rechazo de lote. */
    public static <T> ApiResponse<T> of(boolean success, T data, String message) {
        return ApiResponse.<T>builder()
                .success(success)
                .data(data)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
