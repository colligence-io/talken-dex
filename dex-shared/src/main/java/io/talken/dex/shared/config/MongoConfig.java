package io.talken.dex.shared.config;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataMongoDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * The type Mongo config.
 */
@Configuration
public class MongoConfig extends AbstractMongoClientConfiguration {
	@Autowired
	private VaultSecretReader secretReader;

	private VaultSecretDataMongoDB secretData = null;

	private VaultSecretDataMongoDB secret() {
		if(secretData == null)
			secretData = secretReader.readSecret("mongodb", VaultSecretDataMongoDB.class);
		return secretData;
	}

	@Override
	public MongoClient mongoClient() {
		final List<ServerAddress> addrs = new ArrayList<>();
		for(VaultSecretDataMongoDB.Server server : secret().getServers()) {
			addrs.add(new ServerAddress(server.getAddr(), server.getPort()));
		}

		final MongoCredential credential = MongoCredential.createCredential(secret().getUsername(), secret().getAuthSource(), secret().getPassword().toCharArray());

		MongoClientSettings settings = MongoClientSettings.builder()
				.applyToClusterSettings(builder -> builder.hosts(addrs))
				.credential(credential)
				.build();

		return MongoClients.create(settings);
	}

	@Override
	protected String getDatabaseName() {
		return secret().getDatabase();
	}
}
