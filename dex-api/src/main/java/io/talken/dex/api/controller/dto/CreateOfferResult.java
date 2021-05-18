package io.talken.dex.api.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import lombok.Data;

import java.math.BigDecimal;

/**
 * The type Create offer result.
 */
@Data
public class CreateOfferResult {
	private String taskId;
	private DexTaskTypeEnum taskType;

	private String sellAssetCode;
	private String buyAssetCode;
	private BigDecimal amount;
	private BigDecimal price;

	@JsonIgnore
	private CalculateFeeResult feeResult;

	private Long offerId;
	private BigDecimal madeAmount;
	private Boolean postTxStatus;

    /**
     * Gets sell asset code.
     *
     * @return the sell asset code
     */
    public String getSellAssetCode() {return getFeeResult().getSellAssetCode();}

    /**
     * Gets buy asset code.
     *
     * @return the buy asset code
     */
    public String getBuyAssetCode() {return getFeeResult().getBuyAssetCode();}

    /**
     * Gets fee asset code.
     *
     * @return the fee asset code
     */
    public String getFeeAssetCode() {return getFeeResult().getFeeAssetCode();}

    /**
     * Gets sell amount.
     *
     * @return the sell amount
     */
    public BigDecimal getSellAmount() {return getFeeResult().getSellAmount();}

    /**
     * Gets buy amount.
     *
     * @return the buy amount
     */
    public BigDecimal getBuyAmount() {return getFeeResult().getBuyAmount();}

    /**
     * Gets fee amount.
     *
     * @return the fee amount
     */
    public BigDecimal getFeeAmount() {return getFeeResult().getFeeAmount();}
}
