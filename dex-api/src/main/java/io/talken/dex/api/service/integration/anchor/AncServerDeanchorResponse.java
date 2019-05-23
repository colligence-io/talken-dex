package io.talken.dex.api.service.integration.anchor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.talken.dex.shared.service.integration.CodeMessageResponseInterface;
import lombok.Data;

@Data
@JsonIgnoreProperties("message")
public class AncServerDeanchorResponse implements CodeMessageResponseInterface {
	private String code;
	private String description;
	private _Data data;

	@Override
	public boolean isSuccess() {
		return "200".equals(code);
	}

	@Override
	public String getMessage() {
		return getDescription();
	}

	@Data
	public static class _Data {
		private Integer index;
	}
}
