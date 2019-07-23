package io.talken.dex.governance.config;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataMongoDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;

@Configuration
public class MongoConfig extends AbstractMongoConfiguration {
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
		final ServerAddress addr = new ServerAddress(secret().getAddr(), secret().getPort());
		final MongoCredential credential = MongoCredential.createCredential(secret().getUsername(), secret().getAuthSource(), secret().getPassword().toCharArray());
		final MongoClientOptions options = MongoClientOptions.builder().build();
		return new MongoClient(addr, credential, options);
	}

	@Override
	protected String getDatabaseName() {
		return secret().getDatabase();
	}
}
