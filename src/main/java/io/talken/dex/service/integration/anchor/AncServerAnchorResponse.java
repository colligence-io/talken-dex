package io.talken.dex.service.integration.anchor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.talken.dex.service.integration.CodeMessageResponseInterface;
import lombok.Data;

@Data
@JsonIgnoreProperties("message")
public class AncServerAnchorResponse implements CodeMessageResponseInterface {
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
		private String address;
	}
}
