package io.talken.dex.api.controller;

import io.talken.dex.shared.exception.ParameterViolationException;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

public class DTOValidator {
	private static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();

	public static <T> void validate(T obj) throws ParameterViolationException {
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<T>> violations = validator.validate(obj);
		if(violations.size() > 0) {
			StringBuilder msg = new StringBuilder("\n");
			for(ConstraintViolation<T> _v : violations) {
				msg.append(_v.getPropertyPath().toString()).append(" ").append(_v.getMessage()).append('\n');
			}
			throw new ParameterViolationException(msg.toString());
		}
	}
}
