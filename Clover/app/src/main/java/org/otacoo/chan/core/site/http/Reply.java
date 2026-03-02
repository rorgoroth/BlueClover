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
package org.otacoo.chan.core.site.http;

import org.otacoo.chan.core.model.orm.Loadable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The data needed to send a reply.
 */
public class Reply {
    /**
     * Optional. {@code null} when ReCaptcha v2 was used or a 4pass
     */
    public String captchaChallenge;

    /**
     * Optional. {@code null} when a 4pass was used.
     */
    public String captchaResponse;

    public Loadable loadable;

    // Legacy single file support - maintained for backwards compatibility
    public File file;
    public String fileName = "";
    
    // New multiple file support
    public List<FileAttachment> fileAttachments = new ArrayList<>();

    public String name = "";
    public String options = "";
    public String subject = "";
    public String comment = "";
    public int selectionStart;
    public int selectionEnd;
    public boolean spoilerImage = false;
    public String password = "";
    public String flag = "";

    /**
     * Represents a single file attachment with optional metadata like spoiler status.
     */
    public static class FileAttachment {
        public File file;
        public String fileName;
        public boolean spoiler;

        public FileAttachment(File file, String fileName) {
            this(file, fileName, false);
        }

        public FileAttachment(File file, String fileName, boolean spoiler) {
            this.file = file;
            this.fileName = fileName;
            this.spoiler = spoiler;
        }
    }
}
