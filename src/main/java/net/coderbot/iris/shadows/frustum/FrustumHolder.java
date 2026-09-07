package net.coderbot.iris.shadows.frustum;

import net.minecraft.client.renderer.culling.Frustrum;

public class FrustumHolder {
	private final String role;
	private Frustrum frustum;
	private String distanceInfo = "(unavailable)";
	private String cullingInfo = "(unavailable)";

	public FrustumHolder(String role) {
		this.role = role;
	}

	public FrustumHolder setInfo(Frustrum frustum, String distanceInfo, String cullingInfo) {
		this.frustum = frustum;
		this.distanceInfo = distanceInfo;
		this.cullingInfo = cullingInfo;
		return this;
	}

	public String getRole() {
		return role;
	}

	public Frustrum getFrustum() {
		return frustum;
	}

	public String getDistanceInfo() {
		return distanceInfo;
	}

	public String getCullingInfo() {
		return cullingInfo;
	}
}
