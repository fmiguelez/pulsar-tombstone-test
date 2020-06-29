package es.fmp.pulsar.test;

public class DummyObject {

	private String uuid;
	private long updateTimestamp;
	private double doubleValue;

	public DummyObject() {
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public long getUpdateTimestamp() {
		return updateTimestamp;
	}

	public void setUpdateTimestamp(long updateTimestamp) {
		this.updateTimestamp = updateTimestamp;
	}

	public double getDoubleValue() {
		return doubleValue;
	}

	public void setDoubleValue(double doubleValue) {
		this.doubleValue = doubleValue;
	}

	@Override
	public String toString() {
		return "DummyObject [uuid=" + uuid + ", updateTimestamp=" + updateTimestamp + ", doubleValue=" + doubleValue
				+ "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(doubleValue);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + (int) (updateTimestamp ^ (updateTimestamp >>> 32));
		result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DummyObject other = (DummyObject) obj;
		if (Double.doubleToLongBits(doubleValue) != Double.doubleToLongBits(other.doubleValue))
			return false;
		if (updateTimestamp != other.updateTimestamp)
			return false;
		if (uuid == null) {
			if (other.uuid != null)
				return false;
		} else if (!uuid.equals(other.uuid))
			return false;
		return true;
	}
	
	
}
