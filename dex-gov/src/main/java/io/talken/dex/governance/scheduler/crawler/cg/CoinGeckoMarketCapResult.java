package io.talken.dex.governance.scheduler.crawler.cg;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Data
public class CoinGeckoMarketCapResult {
    private List<MarketCapTypeValue> totalMarketCap;
    private List<MarketCapTypeValue> totalVolume;
    private List<MarketCapTypeValue> marketCapPercentage;
    private BigDecimal marketCapChangePer24hUSD;
    private LocalDateTime updatedAt;

    @Data
    static class MarketCapTypeValue {
        private String symbol;
        private BigDecimal value;
        public MarketCapTypeValue(String symbol, BigDecimal value) {
            this.symbol = symbol;
            this.value = value;
        }
    }

    static List<MarketCapTypeValue> dataListBuilder(JsonObject jsonObject) {
        List<MarketCapTypeValue> list = new ArrayList<>();
        Iterator<Map.Entry<String, JsonElement>> iterator = jsonObject.entrySet().iterator();
        Map.Entry<String, JsonElement> entry;
        while (iterator.hasNext()) {
            entry = iterator.next();
            MarketCapTypeValue typeValue = new MarketCapTypeValue(entry.getKey(), entry.getValue().getAsBigDecimal());
            list.add(typeValue);
        }
        return list;
    }
}
