package org.springframework.lab.acactx;

/**
 * 条件注册的 Bean — 仅在 feature.beta=true 时注册
 */
public class BetaService {

	@Override
	public String toString() {
		return "BetaService (beta feature enabled)";
	}
}
