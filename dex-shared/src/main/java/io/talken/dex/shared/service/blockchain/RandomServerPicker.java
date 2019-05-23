package io.talken.dex.shared.service.blockchain;

import io.talken.common.util.collection.ObjectPair;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RandomServerPicker {
	private List<ObjectPair<String, Boolean>> serverList = new ArrayList<>();
	private SecureRandom random = new SecureRandom();

	public String pick() {
		List<String> availableServers = serverList.stream().filter(_sl -> _sl.second().equals(true)).map(ObjectPair::first).collect(Collectors.toList());
		return availableServers.get(random.nextInt(availableServers.size()));
	}

	public void add(String addr) {
		serverList.add(new ObjectPair<>(addr, true));
	}
}
