package io.talken.dex.shared.service.blockchain.filecoin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The type Cid.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Cid {

    @JsonProperty("/")
    private String root;

    /**
     * Gets root.
     *
     * @return the root
     */
    public String getRoot() {
        return root;
    }

    /**
     * Sets root.
     *
     * @param root the root
     */
    public void setRoot(String root) {
        this.root = root;
    }
}
