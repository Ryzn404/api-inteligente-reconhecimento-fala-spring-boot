package dio.budgeting;

/**
 * Condição para os testes que chamam um serviço de IA de verdade: OpenAI ou Groq (perfil groq).
 */
public final class AiKeys {
    private AiKeys() {
    }

    public static boolean available() {
        return isSet("OPENAI_API_KEY") || (isSet("GROQ_API_KEY") && groqProfileIsActive());
    }

    private static boolean groqProfileIsActive() {
        var profiles = System.getenv("SPRING_PROFILES_ACTIVE");
        return profiles != null && profiles.contains("groq");
    }

    private static boolean isSet(String name) {
        var value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
