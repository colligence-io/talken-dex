package io.talken.dex.shared.service.blockchain.filecoin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.arteam.simplejsonrpc.client.JsonRpcClient;
import com.github.arteam.simplejsonrpc.client.Transport;
import io.talken.common.util.PrefixedLogger;
import kotlin.text.Charsets;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public class FilecoinRpcClient {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(FilecoinRpcClient.class);
    private JsonRpcClient client;

    private static final BigDecimal FIL_UNIT = BigDecimal.TEN.pow(18);

    public FilecoinRpcClient(String uri, String projectId, String projectSecret) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.client = new JsonRpcClient(new BaseTransport(uri, projectId, projectSecret), mapper);
    }

    private class BaseTransport implements Transport {
        private String projectId;
        private String projectSecret;
        private String uri;

        BaseTransport(String uri, String projectId, String projectSecret){
            this.projectId = projectId;
            this.projectSecret = projectSecret;
            this.uri = uri;
        }

        @Override
        public @NotNull String pass(@NotNull String request) throws IOException {
            CredentialsProvider provider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials =
                    new UsernamePasswordCredentials(this.projectId, this.projectSecret);
            provider.setCredentials(AuthScope.ANY,credentials);
            CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setDefaultCredentialsProvider(provider)
                    .build();

            HttpPost post = new HttpPost(this.uri);
            post.setHeader("Accept", "application/json");
            post.setHeader("Content-type", "application/json");
            post.setEntity(new StringEntity(request, Charsets.UTF_8));
            try (CloseableHttpResponse httpResponse = httpClient.execute(post)) {
                HttpEntity entity = httpResponse.getEntity();
                String rslt = EntityUtils.toString(entity, Charsets.UTF_8);
//                logger.debug("result : {}", rslt);
                return rslt;
            }
        }
    }

    public BigDecimal getbalance(String address) {
        String[] addr = {address};
        String balance = null;

        Map<String, Object> result = this.client.createRequest().method("Filecoin.WalletBalance")
                .id(0)
                .params(addr)
                .returnAsMap(HashMap.class, Object.class)
                .execute();

        if (result.get("result") != null)
            balance = result.get("result").toString();

        if (balance != null)
            return (new BigDecimal(balance)).divide(FIL_UNIT, 8, RoundingMode.HALF_DOWN);

        return BigDecimal.ZERO;
    }

    public BigInteger getNonce(String address) {
        String[] addr = {address};
        String nonce = null;

        Map<String, Object> result = this.client.createRequest().method("Filecoin.MpoolGetNonce")
                .id(0)
                .params(addr)
                .returnAsMap(HashMap.class, Object.class)
                .execute();

        if (result.get("result") != null)
            nonce = result.get("result").toString();

        if (nonce != null)
            return new BigDecimal(nonce).toBigInteger();

        return BigInteger.ZERO;
    }

    /**
     * filecoin 전송
     * @param message
     * @return
     */
    public FilecoinTransaction push(String message) {
        FilecoinTransaction result = this.client.createRequest().method("Filecoin.MpoolPush")
                .id(0)
                .params(message)
                .returnAs(FilecoinTransaction.class)
                .execute();
//        //FilecoinMessage.Transaction 객체를 받는듯. 결과값 확인 필요.
//        if (result.getCID() != null)
//            return result.getCID();

        return result;
    }

    public Map<String, Object> getChainHead() {
        return this.client.createRequest().method("Filecoin.ChainHead")
                .id(0)
                .params(new Object[] {})
                .returnAsMap(HashMap.class, Object.class)
                .execute();
    }

    public TipSet getChainGetTipSetByHeight(BigInteger height) {
        TipSet rslt = this.client.createRequest().method("Filecoin.ChainGetTipSetByHeight")
                .id(0)
                .params(new Object[] {height, null})
                .returnAs(TipSet.class)
                .execute();
        return rslt;
    }

    public BlockMessages getChainGetBlockMessages(Cid cid) {
        BlockMessages rslt = this.client.createRequest().method("Filecoin.ChainGetBlockMessages")
                .id(0)
                .params(cid)
                .returnAs(BlockMessages.class)
                .execute();
        return rslt;
    }

    public FilecoinMessage.Message getChainGetMessage(Cid cid) {
        FilecoinMessage.Message rslt = this.client.createRequest().method("Filecoin.ChainGetMessage")
                .id(0)
                .params(cid)
                .returnAs(FilecoinMessage.Message.class)
                .execute();
        return rslt;
    }
}
