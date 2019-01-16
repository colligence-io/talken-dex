package io.colligence.talken.dex.service;

import io.colligence.talken.common.CLGException;
import io.colligence.talken.common.CommonConsts;
import org.springframework.context.MessageSource;

import java.util.Locale;

public class MessageService {
	private MessageSource messageSource;
	private Locale defaultLocale;

	public MessageService(Locale defaultLocale, MessageSource messageSource) {
		this.defaultLocale = defaultLocale;
		this.messageSource = messageSource;

		checkErrorMessages();
	}

	public String getMessage(Locale locale, String messageID, Object... args) {
		return messageSource.getMessage(messageID, args, locale);
	}

	public String getMessage(String messageID, Object... args) {
		return getMessage(defaultLocale, messageID, args);
	}

	public String getMessage(Enum<?> e) {
		return getMessage(getEnumKey(e));
	}

	public String getMessage(Locale locale, Enum<?> e) {
		return getMessage(locale, getEnumKey(e));
	}

	public String getMessage(Locale locale, CLGException ce) {
		Object[] args = new Object[ce.getArgs().length];
		for(int i = 0; i < ce.getArgs().length; i++) {
			if(ce.getArgs()[i] instanceof MessageEnumInterface) {
				args[i] = getMessage((Enum) (ce.getArgs()[i]));
			} else {
				args[i] = ce.getArgs()[i];
			}
		}
		return getMessage(locale, ce.getType().getMessageKey(), args);
	}

	private String getEnumKey(Enum<?> e) {
		return CommonConsts.ENUM_MESSAGEKEY_PREFIX.concat(".").concat(e.getClass().getSimpleName()).concat(".").concat(e.name());
	}

	public interface MessageEnumInterface {
	}

	private void checkErrorMessages() throws AssertionError {

	}
}
