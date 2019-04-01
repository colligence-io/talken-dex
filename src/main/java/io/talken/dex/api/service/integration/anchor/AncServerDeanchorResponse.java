package io.talken.dex.api.service.integration.anchor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.talken.dex.api.service.integration.CodeMessageResponseInterface;
import lombok.Data;

@Data
@JsonIgnoreProperties("message")
public class AncServerDeanchorResponse implements CodeMessageResponseInterface {
	private int code;
	private String description;
	private _Data data;

	@Override
	public boolean isSuccess() {
		return code == 200;
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
