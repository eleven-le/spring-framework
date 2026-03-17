package org.springframework.lab.dynamicregistry;

public class PluginBean {

	private final String name;

	public PluginBean(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
}
