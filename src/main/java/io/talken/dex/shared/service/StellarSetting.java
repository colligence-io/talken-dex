package io.talken.dex.shared.service;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties("talken.dex.stellar")
@Getter
@Setter
public class StellarSetting {
	private String network;
	private List<String> serverList;
}
