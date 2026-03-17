package org.springframework.lab.environment;

public class NotificationService {

	private final String channel;

	public NotificationService() {
		this.channel = "DEFAULT";
	}

	public NotificationService(String channel) {
		this.channel = channel;
	}

	public String getChannel() {
		return channel;
	}
}
