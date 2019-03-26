package io.colligence.talken.dex;

import io.colligence.talken.common.Bootstrap;
import io.colligence.talken.common.CommonConsts;
import io.colligence.talken.dex.config.auth.AccessTokenInterceptor;
import io.colligence.talken.dex.exception.AccountNotFoundException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;

@SpringBootApplication(scanBasePackages = {"io.colligence.talken.dex"}, exclude = {ErrorMvcAutoConfiguration.class})
public class DexLauncher {
	public static void main(String[] args) {
		Bootstrap bootstrap = new Bootstrap(DexLauncher.class, args);
		// Parse arguments
		for(int i = 0; i < args.length; i++) {
			// force uid
			if(args[i].equals("-u")) {
				i++;
				if(args.length <= i) usage();
				try {
					AccessTokenInterceptor.setAuthSkipper(Long.valueOf(args[i]));
				} catch(Exception ex) {
					usage();
					bootstrap.shutdown(CommonConsts.EXITCODE.ARGUMENT_ERROR);
				}
			}
		}

		DexException de = new AccountNotFoundException("1", "2");

		bootstrap.startup();
	}

	private static void usage() {
		System.out.println("Colligence API Server");
		System.out.println("Usage :");
		System.out.println("  -p : profile [(l)ocal,(d)ev,(t)est,(p)roduction]");
		System.out.println("  -v : verbose mode");
		System.out.println("  -u : skip auth with given uid");
	}
}
