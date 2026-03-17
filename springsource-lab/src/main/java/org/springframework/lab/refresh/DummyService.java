package org.springframework.lab.refresh;

import org.springframework.stereotype.Component;

@Component
public class DummyService {

	public String ping() {
		return "pong";
	}
}
