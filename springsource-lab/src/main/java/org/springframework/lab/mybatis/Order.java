package org.springframework.lab.mybatis;

/**
 * 订单实体 — 对应 t_order 表
 */
public class Order {

	private Long id;
	private String orderNo;
	private Long userId;
	private Long amount;
	private String status;

	public Order() {
	}

	public Order(String orderNo, Long userId, Long amount) {
		this.orderNo = orderNo;
		this.userId = userId;
		this.amount = amount;
		this.status = "CREATED";
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getOrderNo() { return orderNo; }
	public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public Long getAmount() { return amount; }
	public void setAmount(Long amount) { this.amount = amount; }

	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }

	@Override
	public String toString() {
		return "Order{id=" + id + ", orderNo='" + orderNo + "', userId=" + userId
				+ ", amount=" + amount + ", status='" + status + "'}";
	}
}
