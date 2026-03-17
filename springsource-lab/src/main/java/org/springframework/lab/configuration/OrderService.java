package org.springframework.lab.configuration;

public class OrderService {

	private final DataSource dataSource;

	public OrderService(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	@Override
	public String toString() {
		return "OrderService{dataSource=" + dataSource + "}";
	}
}
