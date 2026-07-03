package mx.personas.api.common;

/**
 * Constantes compartidas para pruebas @WebMvcTest, que si cargan el ApiKeyAuthFilter
 * (Filter es parte del slice por defecto de @WebMvcTest) y por lo tanto requieren el
 * header X-API-Key en cada request.
 */
public final class TestApiKey {

    public static final String HEADER = "X-API-Key";
    public static final String VALOR = "test-api-key";

    private TestApiKey() {
    }
}
