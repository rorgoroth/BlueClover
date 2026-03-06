/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.otacoo.chan.controller.transition;

import org.otacoo.chan.controller.ControllerTransition;

public class PopControllerTransition extends ControllerTransition {
    public PopControllerTransition() {
        viewOver = false;
    }

    @Override
    public void perform() {
        if (from != null && from.view != null) {
            from.view.setAlpha(0f);
        }
        if (to != null && to.view != null) {
            to.view.setAlpha(1f);
            to.view.setScaleX(1f);
            to.view.setScaleY(1f);
            to.view.setTranslationY(0f);
        }
        onCompleted();
    }
}
