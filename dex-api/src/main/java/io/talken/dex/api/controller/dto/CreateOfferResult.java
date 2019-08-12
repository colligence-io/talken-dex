package io.talken.dex.api.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOfferResult {
	private String taskId;
	private String transId;

	private DexTaskTypeEnum taskType;

	private BigDecimal amount;
	private BigDecimal price;

	@JsonIgnore
	private CalculateFeeResult feeResult;

	public String getSellAssetCode() {return getFeeResult().getSellAssetCode();}

	public String getBuyAssetCode() {return getFeeResult().getBuyAssetCode();}

	public String getFeeAssetCode() {return getFeeResult().getFeeAssetCode();}

	public BigDecimal getSellAmount() {return getFeeResult().getSellAmount();}

	public BigDecimal getBuyAmount() {return getFeeResult().getBuyAmount();}

	public BigDecimal getFeeAmount() {return getFeeResult().getFeeAmount();}
}
