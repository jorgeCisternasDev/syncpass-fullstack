package cl.duoc.syncpass.sync.dto;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Exige texto JSON estricto para {@code usuarioUuid}: un número o un objeto no se
 * convierten en silencio, porque el contrato v2 los trata como error de formato (400).
 * Jackson 3 (Spring Boot 4) nombra esta clase {@link ValueDeserializer}.
 */
public class TextoEstrictoDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            return parser.getString();
        }
        return context.reportInputMismatch(this, "usuarioUuid debe ser texto");
    }
}
