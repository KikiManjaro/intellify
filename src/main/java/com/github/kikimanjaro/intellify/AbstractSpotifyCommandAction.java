package com.github.kikimanjaro.intellify;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.sun.jna.platform.win32.WinDef;

/**
 * Created by rozsenichb on 10/02/2016.
 */
public abstract class AbstractSpotifyCommandAction extends AnAction {

	protected boolean active = false;

	public void setActive(boolean active) {
		this.active = active;
	}
}
