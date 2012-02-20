/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package com.oracle.graal.visualizer.snapshots;

import com.oracle.graal.visualizer.editor.EditorTopComponent;
import com.oracle.graal.visualizer.util.LookupUtils;
import com.sun.hotspot.igv.data.ChangedEvent;
import com.sun.hotspot.igv.data.ChangedListener;
import com.sun.hotspot.igv.util.RangeSlider;
import com.sun.hotspot.igv.util.RangeSliderModel;
import java.awt.BorderLayout;
import javax.swing.JScrollPane;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.Lookup.Result;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

@TopComponent.Description(preferredID = SnapshotTopComponent.PREFERRED_ID, persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "belowExplorer", openAtStartup = true)
@ActionID(category = "Window", id = "com.oracle.graal.visualizer.snapshots.SnapshotTopComponent")
@ActionReference(path = "Menu/Window")
@TopComponent.OpenActionRegistration(displayName = "Snapshot", preferredID = SnapshotTopComponent.PREFERRED_ID)
public final class SnapshotTopComponent extends TopComponent {
    public static final String PREFERRED_ID = "SnapshotTopComponent";

    private final Result<RangeSliderModel> result;
    private final RangeSlider rangeSlider;
    private final ChangedEvent<RangeSliderModel> rangeSliderChangedEvent = new ChangedEvent<RangeSliderModel>(null);
    private final LookupListener lookupListener = new LookupListener() {

        @Override
        public void resultChanged(LookupEvent le) {
            update();
        }
    };

    private final ChangedListener<RangeSliderModel> rangeSliderChangedListener = new ChangedListener<RangeSliderModel>(){

        @Override
        public void changed(RangeSliderModel source) {
            rangeSliderChangedEvent.fire();
        }
    };

    public SnapshotTopComponent() {
        initComponents();
        setName("Snapshot Window");
        setToolTipText("This is a Snapshot window");

        result = LookupUtils.getLastActiveDelegatingLookup(EditorTopComponent.class).lookupResult(RangeSliderModel.class);
        result.addLookupListener(lookupListener);
        this.rangeSlider = new RangeSlider(null);
        this.setLayout(new BorderLayout());
        final JScrollPane scrollPane = new JScrollPane(rangeSlider);
        scrollPane.getVerticalScrollBar().setUnitIncrement(RangeSlider.ITEM_HEIGHT);
        this.add(scrollPane, BorderLayout.CENTER);
        update();
    }

    private void update() {
        RangeSliderModel newModel;
        if (result.allInstances().size() > 0) {
            newModel = result.allInstances().iterator().next();
        } else {
            newModel = null;
        }
        if (rangeSlider.getModel() != null) {
            rangeSlider.getModel().getChangedEvent().removeListener(rangeSliderChangedListener);
        }
        rangeSlider.setModel(newModel);
        rangeSliderChangedEvent.changeObject(newModel);
        if (newModel != null) {
            newModel.getChangedEvent().addListener(rangeSliderChangedListener);
        }
    }

    public ChangedEvent<RangeSliderModel> getRangeSliderChangedEvent() {
        return rangeSliderChangedEvent;
    }

    public static SnapshotTopComponent findInstance() {
        return (SnapshotTopComponent) WindowManager.getDefault().findTopComponent(PREFERRED_ID);
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

}
