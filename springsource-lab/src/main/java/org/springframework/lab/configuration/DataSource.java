package org.springframework.lab.configuration;

public class DataSource {

	private final String url;

	public DataSource(String url) {
		this.url = url;
	}

	@Override
	public String toString() {
		return "DataSource{url='" + url + "'}";
	}
}
