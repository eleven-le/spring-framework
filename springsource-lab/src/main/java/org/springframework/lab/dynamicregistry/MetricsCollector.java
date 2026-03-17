package org.springframework.lab.dynamicregistry;

public class MetricsCollector {

	private final String endpoint;

	public MetricsCollector(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getEndpoint() {
		return endpoint;
	}

	@Override
	public String toString() {
		return "MetricsCollector{endpoint='" + endpoint + "'}";
	}
}
