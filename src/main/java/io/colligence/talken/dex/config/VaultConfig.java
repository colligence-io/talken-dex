package io.colligence.talken.dex.config;

import com.google.common.hash.Hashing;
import io.colligence.talken.common.RunningProfile;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class VaultConfig extends AbstractVaultConfiguration {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(VaultConfig.class);
	@Value("${vault.uri}")
	private URI vaultUri;

	@Value("${vault.app-role}")
	private String appRole;

	@Value("${vault.secret-key}")
	private String password;


	@Override
	public VaultEndpoint vaultEndpoint() {
		return VaultEndpoint.from(vaultUri);
	}

	private VaultToken getInitialToken(String username, String password) {
		ResponseEntity<VaultResponse> entity = restOperations().exchange(
				"auth/userpass/login/" + username, HttpMethod.POST,
				new HttpEntity<>(new HashMap.SimpleEntry<>("password", password)),
				VaultResponse.class);

		if(entity.getBody() != null) {
			Map<String, Object> data = entity.getBody().getAuth();
			if(data != null) {
				return VaultToken.of(data.get("client_token").toString());
			} else return null;
		} else return null;
	}

	@Override
	public ClientAuthentication clientAuthentication() {
		String username;
		if(RunningProfile.isLocal()) {
			username = appRole;
		} else {
			username = Hashing.sha256().hashString(DexLauncher.HOSTNAME, StandardCharsets.UTF_8).toString().toLowerCase();
		}

		VaultToken initialToken = getInitialToken(username, password);
		AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
				.appRole(appRole)
				.roleId(AppRoleAuthenticationOptions.RoleId.pull(initialToken))
				.secretId(AppRoleAuthenticationOptions.SecretId.pull(initialToken))
				.build();
		return new AppRoleAuthentication(options, restOperations());
	}

	@Bean
	public VaultSecretReader vaultSecretReader() {
		return new VaultSecretReader(vaultTemplate());
	}

	public static class VaultSecretReader {
		private VaultTemplate vaultTemplate;

		public VaultSecretReader(VaultTemplate vaultTemplate) {
			this.vaultTemplate = vaultTemplate;
		}

		public <T> T readSecret(String name, Class<T> clz) throws SecretAccessException {
			try {
				VaultResponseSupport<T> vr = vaultTemplate.read("secret/credentials/" + name, clz);
				T data = vr.getData();
				if(data == null)
					throw new SecretAccessException("Cannot get " + name + " secret credentials : no data");
				return data;
			} catch(RuntimeException ex) {
				throw new SecretAccessException("Cannot get " + name + " secret credentials : " + ex.getMessage(), ex);
			}
		}
	}
}
