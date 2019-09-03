package io.talken.dex.api.service;


import io.talken.dex.shared.exception.SwapPathNotAvailableException;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

@Data
public class SwapPredictSet implements Serializable {
	private static final long serialVersionUID = 8475187678890154170L;
	private SortedSet<Ask> asks;
	private String baseAssetCode;
	private String counterAssetCode;

	public BigDecimal predict(BigDecimal amount) throws SwapPathNotAvailableException {
		Iterator<Ask> iterator = asks.iterator();

		BigDecimal remain = amount.setScale(18, RoundingMode.FLOOR);
		BigDecimal result = BigDecimal.ZERO;

		while(iterator.hasNext()) {
			Ask a = iterator.next();

			if(a.amount.compareTo(remain) < 0) {
				remain = remain.subtract(a.amount);
				result = result.add(a.amount.divide(a.price, RoundingMode.FLOOR));
			} else {
				return result.add(remain.divide(a.price, RoundingMode.FLOOR));
			}
		}
		throw new SwapPathNotAvailableException(baseAssetCode, counterAssetCode, amount);
	}

	public void addAsk(BigDecimal price, BigDecimal amount) {
		if(asks == null) asks = new TreeSet<>();
		Ask ask = new Ask();
		ask.price = price;
		ask.amount = amount;
		asks.add(ask);
	}

	@Data
	public static class Ask implements Comparable<Ask>, Serializable {
		private static final long serialVersionUID = 4846192378701285993L;

		private BigDecimal price;
		private BigDecimal amount;

		@Override
		public int compareTo(@NotNull Ask ask) {
			return price.compareTo(ask.price);
		}
	}
}
