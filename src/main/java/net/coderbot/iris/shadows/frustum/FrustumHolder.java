package net.coderbot.iris.shadows.frustum;

import net.minecraft.client.renderer.culling.Frustrum;

public class FrustumHolder {
	private Frustrum frustum;
	private String distanceInfo = "(unavailable)";
	private String cullingInfo = "(unavailable)";

	public FrustumHolder setInfo(Frustrum frustum, String distanceInfo, String cullingInfo) {
		this.frustum = frustum;
		this.distanceInfo = distanceInfo;
		this.cullingInfo = cullingInfo;
		return this;
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
