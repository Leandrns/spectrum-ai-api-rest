package com.spectrumai.backend.auth.password;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validator de {@link StrongPassword}. Aplica as regras de seguranca da
 * politica de senha do Spectrum.
 *
 * <p>Quando a senha falha, substitui a mensagem default da annotation pelo
 * motivo especifico (faltando especial / contem sequencia / etc.) para que
 * o usuario saiba o que corrigir.</p>
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    /** Tamanho minimo e maximo (alinhado com @Size do DTO). */
    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    private static final Pattern LOWER   = Pattern.compile("[a-z]");
    private static final Pattern UPPER   = Pattern.compile("[A-Z]");
    private static final Pattern DIGIT   = Pattern.compile("\\d");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern REPEAT  = Pattern.compile("(.)\\1\\1"); // 3+ chars iguais

    /**
     * Lista negra de senhas reconhecidamente fracas que passam nas regras
     * de complexidade. Curada para o publico-alvo (BR + corporativo).
     * A comparacao e feita case-insensitive e desconsiderando o caractere especial.
     */
    private static final Set<String> BLACKLIST = Set.of(
            "password1",   "password123",  "password2024", "password2025",
            "passw0rd",    "p@ssw0rd",     "passw0rd1",
            "qwerty1",     "qwerty123",    "qwertyuiop1",
            "welcome1",    "welcome123",   "welcomeback1",
            "admin1",      "admin123",     "administrator1",
            "letmein1",    "iloveyou1",    "monkey1",
            "abcd1234",    "abcdef1",      "asdf1234",
            "spectrum1",   "spectrum123",  "automotivo1",
            "senha123",    "senha1234",    "senhaforte1",
            "123456789a",  "1234567890a",  "qazwsx1",
            "trustno1",    "master1",      "dragon1"
    );

    /**
     * Sequencias triviais comuns. Verifica substring case-insensitive de
     * 4+ caracteres em ordem (asc ou desc) no teclado/numerico/alfabeto.
     */
    private static final String[] TRIVIAL_SEQUENCES = {
            "0123456789", "9876543210",
            "abcdefghijklmnopqrstuvwxyz",
            "zyxwvutsrqponmlkjihgfedcba",
            "qwertyuiop", "asdfghjkl", "zxcvbnm"
    };

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) {
            return setReason(ctx, "informe a senha");
        }
        if (value.length() < MIN_LENGTH) {
            return setReason(ctx, "a senha deve ter ao menos " + MIN_LENGTH + " caracteres");
        }
        if (value.length() > MAX_LENGTH) {
            return setReason(ctx, "a senha nao pode passar de " + MAX_LENGTH + " caracteres");
        }
        if (!LOWER.matcher(value).find()) {
            return setReason(ctx, "a senha deve conter ao menos 1 letra minuscula");
        }
        if (!UPPER.matcher(value).find()) {
            return setReason(ctx, "a senha deve conter ao menos 1 letra maiuscula");
        }
        if (!DIGIT.matcher(value).find()) {
            return setReason(ctx, "a senha deve conter ao menos 1 digito");
        }
        if (!SPECIAL.matcher(value).find()) {
            return setReason(ctx, "a senha deve conter ao menos 1 caractere especial");
        }
        if (REPEAT.matcher(value).find()) {
            return setReason(ctx, "a senha nao pode ter 3 caracteres iguais em sequencia");
        }
        if (containsTrivialSequence(value)) {
            return setReason(ctx, "a senha nao pode conter sequencias triviais (1234, abcd, qwerty)");
        }
        if (isInBlacklist(value)) {
            return setReason(ctx, "esta senha e comum demais - escolha outra");
        }
        return true;
    }

    private boolean containsTrivialSequence(String password) {
        String lower = password.toLowerCase();
        for (String seq : TRIVIAL_SEQUENCES) {
            for (int i = 0; i <= seq.length() - 4; i++) {
                String chunk = seq.substring(i, i + 4);
                if (lower.contains(chunk)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isInBlacklist(String password) {
        // Normaliza: lowercase + remove especiais comuns (!@#$ etc.) para
        // pegar variacoes triviais como "P@ssw0rd!" -> "passw0rd"
        String normalized = password
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        return BLACKLIST.contains(normalized);
    }

    private boolean setReason(ConstraintValidatorContext ctx, String reason) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(reason).addConstraintViolation();
        return false;
    }
}
