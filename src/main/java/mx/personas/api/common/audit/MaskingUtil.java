package mx.personas.api.common.audit;

/**
 * Enmascarado parcial de campos sensibles (CURP, RFC, telefono) antes de persistirlos en
 * persona_historial (research.md §5 de specs/003-auditoria-personas): conserva los
 * primeros y ultimos 2 caracteres, sustituye el resto por '*'.
 */
public final class MaskingUtil {

    private static final int PREFIJO = 2;
    private static final int SUFIJO = 2;

    private MaskingUtil() {
    }

    public static String enmascarar(String valor) {
        if (valor == null) {
            return null;
        }
        if (valor.length() <= PREFIJO + SUFIJO) {
            return "*".repeat(valor.length());
        }
        String inicio = valor.substring(0, PREFIJO);
        String fin = valor.substring(valor.length() - SUFIJO);
        String asteriscos = "*".repeat(valor.length() - PREFIJO - SUFIJO);
        return inicio + asteriscos + fin;
    }
}
