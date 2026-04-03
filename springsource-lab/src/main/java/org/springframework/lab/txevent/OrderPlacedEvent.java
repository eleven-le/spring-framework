package org.springframework.lab.txevent;

/**
 * 轻量领域事件 — 不继承 ApplicationEvent（4.2+ 自动包装为 PayloadApplicationEvent）。
 * 在所有实验中复用此事件。
 */
public class OrderPlacedEvent {

	private final String orderId;
	private final String item;
	private final int price;
	private final String scenario;

	public OrderPlacedEvent(String orderId, String item, int price, String scenario) {
		this.orderId = orderId;
		this.item = item;
		this.price = price;
		this.scenario = scenario;
	}

	public String getOrderId() { return orderId; }
	public String getItem() { return item; }
	public int getPrice() { return price; }
	public String getScenario() { return scenario; }

	@Override
	public String toString() {
		return "OrderPlacedEvent{id='" + orderId + "', item='" + item + "', price=" + price
				+ ", scenario='" + scenario + "'}";
	}
}
