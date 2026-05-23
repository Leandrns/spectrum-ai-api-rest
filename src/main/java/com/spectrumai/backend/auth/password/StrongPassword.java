package com.spectrumai.backend.auth.password;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aplica a politica de senha forte do Spectrum em um campo String.
 *
 * <p>Regras (ver {@link StrongPasswordValidator} para detalhes):</p>
 * <ul>
 *   <li>10 a 128 caracteres</li>
 *   <li>contem maiuscula, minuscula, digito e caractere especial</li>
 *   <li>nao contem 3 ou mais caracteres iguais em sequencia</li>
 *   <li>nao contem sequencias triviais (1234, abcd, qwerty)</li>
 *   <li>nao esta em lista negra de senhas comuns</li>
 * </ul>
 *
 * <p>A regra "senha nao pode conter email/nome do usuario" e aplicada
 * separadamente no service, pois exige acesso aos demais campos do request.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {

    String message() default "senha nao atende a politica de seguranca";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
