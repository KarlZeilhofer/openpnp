/*
 * Copyright (C) 2017 Karl Zeilhofer <zeilhofer at team14.at>
 * based on the AdvancedLoosePartFeederConfigurationWizard from Jason von Nieda <jason@vonnieda.org>, 2011
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */
package org.openpnp.machine.reference.feeder.wizards;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;


import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.ComponentDecorators;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.gui.support.DoubleConverter;
import org.openpnp.gui.support.MutableLocationProxy;
import org.openpnp.machine.reference.feeder.HeapFeeder;
import org.openpnp.model.Configuration;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.ui.CvPipelineEditor;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;


// TODO 0: implement this wizard, it is only a copy form the AdvancedLoosePartFeeder

public class HeapFeederConfigurationWizard        
		extends AbstractReferenceFeederConfigurationWizard {

	private final HeapFeeder feeder;
	
	// DevPanel input fields:
	
	JTextField tfPumpName;
	JTextField tfValveName;
	JTextField tfPressureSensorName;
	JTextField tfPressureDelta;
	JTextField tfMaxZTravel;
	JTextField tfLastCatchZDepth;
	JTextField tfZStepOnPickup;
	JTextField tfDwellOnZStep;
	
	JTextField tfSubBoxName; // L or R
	JTextField tfboxTrayId; // L or R
	
	JButton bSetDefaultLocation; // use location based on the BoxTrayLocation and the box name

	
	JButton bEditUpsideUp;
	JTextField tfUseUpsideUpPipelineFrom;
	
	JCheckBox cbEnableUpsideDown;
	JButton bEditUpsideDown;
	JTextField tfUseUpsideDownPipelineFrom;
	
	JButton bEditAnythingElse;
	JTextField tfUseAnythingElsePipelineFrom;
	
	JCheckBox cbEnableSideView;
	JButton bEditSideView;
	JTextField tfUseSideViewPipelineFrom;
	
	JButton bMoveToDropBox;
	JButton bMoveToSideView;
	

    public HeapFeederConfigurationWizard(HeapFeeder feeder) {
        super(feeder);
        this.feeder = feeder;
        
        
        
        
        
        
        JPanel devPanel = new JPanel();
        devPanel.setBorder(new TitledBorder(null, "Development", TitledBorder.LEADING, TitledBorder.TOP,
                null, null));
        contentPanel.add(devPanel);
        devPanel.setLayout(
        		new FormLayout(
        				new ColumnSpec[] {
        						FormSpecs.RELATED_GAP_COLSPEC,
        						FormSpecs.DEFAULT_COLSPEC,
        						FormSpecs.RELATED_GAP_COLSPEC,
        						FormSpecs.DEFAULT_COLSPEC,
        						FormSpecs.RELATED_GAP_COLSPEC,
        						FormSpecs.DEFAULT_COLSPEC,},
        				new RowSpec[] {
           						FormSpecs.RELATED_GAP_ROWSPEC,
        						FormSpecs.DEFAULT_ROWSPEC,
           						FormSpecs.RELATED_GAP_ROWSPEC,
        						FormSpecs.DEFAULT_ROWSPEC,
        						FormSpecs.RELATED_GAP_ROWSPEC,
        						FormSpecs.DEFAULT_ROWSPEC,
        						FormSpecs.RELATED_GAP_ROWSPEC,
        						FormSpecs.DEFAULT_ROWSPEC,
        						FormSpecs.RELATED_GAP_ROWSPEC,
        						FormSpecs.DEFAULT_ROWSPEC,
        						FormSpecs.RELATED_GAP_ROWSPEC,
        						FormSpecs.DEFAULT_ROWSPEC,
        						FormSpecs.RELATED_GAP_ROWSPEC,
        						FormSpecs.DEFAULT_ROWSPEC,
        						FormSpecs.RELATED_GAP_ROWSPEC,
        						FormSpecs.DEFAULT_ROWSPEC,
        						FormSpecs.RELATED_GAP_ROWSPEC,
        						FormSpecs.DEFAULT_ROWSPEC,
        						FormSpecs.RELATED_GAP_ROWSPEC,
        						FormSpecs.DEFAULT_ROWSPEC,
        						FormSpecs.RELATED_GAP_ROWSPEC,
        						FormSpecs.DEFAULT_ROWSPEC,}));
        int row = 1;
        int col = 1;
        {
	        JLabel lbl = new JLabel("Box Tray ID");
	        devPanel.add(lbl, "2, "+ Integer.toString(row*2)+", right, default");
	
	        JTextField tf = tfboxTrayId = new JTextField();
	        tf.setText("1");
	        devPanel.add(tf, "4, "+ Integer.toString(row*2)+", fill, default");
	        tf.setColumns(3);
	        
	        row++;
        }
        
        {
	        JLabel lbl = new JLabel("Sub Box");
	        devPanel.add(lbl, "2, "+ Integer.toString(row*2)+", right, default");
	
	        JTextField tf = tfSubBoxName = new JTextField();
	        tf.setText("A1");
	        devPanel.add(tf, "4, "+ Integer.toString(row*2)+", fill, default");
	        tf.setColumns(3);
	        	        
	        row++;
        }
        
        {
	        JButton b = bSetDefaultLocation = new JButton("Set Default Location");
	        b.addActionListener(new BtnSetDefaultLocationActionListener());
	        devPanel.add(b, "4, " + Integer.toString(row*2) + ", fill, default");
	        
	        row++;
        }
      
// TODO 5: reenable these fields, or get the values from somewhere else
//        {
//	        JLabel lbl = new JLabel("Pump Name");
//	        devPanel.add(lbl, "2, "+ Integer.toString(row*2)+", right, default");
//	
//	        JTextField tf = tfPumpName = new JTextField();
//	        tf.setText("Pumpe");
//	        devPanel.add(tf, "4, "+ Integer.toString(row*2)+", fill, default");
//	        tf.setColumns(3);
//	        
//	        row++;
//        }
//        
//        {
//	        JLabel lbl = new JLabel("Valve Name");
//	        devPanel.add(lbl, "2, "+ Integer.toString(row*2)+", right, default");
//	
//	        JTextField tf = tfValveName = new JTextField();
//	        tf.setText("Ventil");
//	        devPanel.add(tf, "4, "+ Integer.toString(row*2)+", fill, default");
//	        tf.setColumns(3);
//	        
//	        row++;
//
//        }
//        
//     
//        {
//	        JLabel lbl = new JLabel("Pressure Sensor Name");
//	        devPanel.add(lbl, "2, "+ Integer.toString(row*2)+", right, default");
//	
//	        JTextField tf = tfPressureSensorName = new JTextField();
//	        devPanel.add(tf, "4, "+ Integer.toString(row*2)+", fill, default");
//	        tf.setColumns(3);
//	        
//	        row++;
//
//        }
//        
     
        {
	        JLabel lbl = new JLabel("Pressure Delta");
	        devPanel.add(lbl, "2, "+ Integer.toString(row*2)+", right, default");
	
	        JTextField tf = tfPressureDelta = new JTextField();
	        devPanel.add(tf, "4, "+ Integer.toString(row*2)+", fill, default");
	        tf.setColumns(3);
	        
	        row++;
        }
        
     
        {
	        JLabel lbl = new JLabel("max. Z-travel");
	        devPanel.add(lbl, "2, "+ Integer.toString(row*2)+", right, default");

	        JTextField tf = tfMaxZTravel = new JTextField();
	        devPanel.add(tf, "4, "+ Integer.toString(row*2)+", fill, default");
	        tf.setColumns(3);
	        
	        row++;
        }
        
     
        {
	        JLabel lbl = new JLabel("Last Catch Z-Depth");
	        devPanel.add(lbl, "2, "+ Integer.toString(row*2)+", right, default");
	        
	        JTextField tf = tfLastCatchZDepth = new JTextField();
	        devPanel.add(tf, "4, "+ Integer.toString(row*2)+", fill, default");
	        tf.setColumns(3);
	        
	        row++;
        }
        
     
        {
	        JLabel lbl = new JLabel("Z step on pickup");
	        devPanel.add(lbl, "2, "+ Integer.toString(row*2)+", right, default");
	        
	        JTextField tf = tfZStepOnPickup = new JTextField();
	        devPanel.add(tf, "4, "+ Integer.toString(row*2)+", fill, default");
	        tf.setColumns(3);
	        
	        row++;
        }
        
     
        {
	        JLabel lbl = new JLabel("dwell after Z-step [ms]");
	        devPanel.add(lbl, "2, "+ Integer.toString(row*2)+", right, default");
	        
	        JTextField tf = tfDwellOnZStep = new JTextField();
	        devPanel.add(tf, "4, "+ Integer.toString(row*2)+", fill, default");
	        tf.setColumns(3);
	        
	        row++;
        }
        
     
        
        
        
        

        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(null, "Vision Pipelines", TitledBorder.LEADING, TitledBorder.TOP,
                null, null));
        contentPanel.add(panel);
        panel.setLayout(new FormLayout(new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC, // Pipeline Name
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC, // Enabled Checkbox
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC, // Edit Button
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC, // Use From Label
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC, // Text Field: use from...
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC, // Text Field: use from...
                }, 
            new RowSpec[] {
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC, // Table Headers
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC, // Upside Up
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC, // Upside Down
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC, // Anything Else
	                FormSpecs.RELATED_GAP_ROWSPEC,
	                FormSpecs.DEFAULT_ROWSPEC, // Side View
	                FormSpecs.RELATED_GAP_ROWSPEC,
	                FormSpecs.DEFAULT_ROWSPEC, // Move To Buttons
	                })); 

        row = 1;
        col = 1;
        {
        	col++;
        	
	        panel.add(new JLabel("Enabled"), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", center, default");
	        col++;
	        
	        col++;
	        
	        panel.add(new JLabel("Get it from other HeapFeeder"), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", center, default");
	        col++;
	        
        }

        
        row = 2;
        col = 1;
        {
	        panel.add(new JLabel("Upside Up: "), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", right, default");
	        col++;

//	        panel.add(new JCheckBox("Enabled"), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", right, default");
	        col++;

	        panel.add(bEditUpsideUp = new JButton("Edit"), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", center, default");
	        bEditUpsideUp.addActionListener(new BtnEditUpsideUpPipelineActionListener());
	        col++;

	        panel.add(tfUseUpsideUpPipelineFrom = new JTextField("NO_REFERRED_FEEDER"), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", fill, default");
	        col++;
        }

        
        row = 3;
        col = 1;
        {
	        panel.add(new JLabel("Upside Down:"), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", right, default");
	        col++;

	        panel.add(cbEnableUpsideDown = new JCheckBox("Enabled", true), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", left, default");
	        col++;

	        panel.add(bEditUpsideDown = new JButton("Edit"), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", center, default");
	        bEditUpsideDown.addActionListener(new BtnEditUpsideDownPipelineActionListener());
	        col++;

	        panel.add(tfUseUpsideDownPipelineFrom = new JTextField(), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", fill, default");
	        col++;
	        
        }
    	
        row = 4;
        col = 1;
        {
	        panel.add(new JLabel("Anything Else: "), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", right, default");
	        col++;

//	        panel.add(new JCheckBox("Enabled", true), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", right, default");
	        col++;

	        panel.add(bEditAnythingElse = new JButton("Edit"), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", center, default");
	        bEditAnythingElse.addActionListener(new BtnEditAnythingElsePipelineActionListener());
	        col++;

	        panel.add(tfUseAnythingElsePipelineFrom = new JTextField(), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", fill, default");
	        col++;
	        
        }

        
        row = 5;
        col = 1;
        {
	        panel.add(new JLabel("Side View: "), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", right, default");
	        col++;

	        panel.add(cbEnableSideView = new JCheckBox("Enabled", false), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", left, default");
	        col++;

	        panel.add(bEditSideView = new JButton("Edit"), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", center, default");
	        bEditSideView.addActionListener(new BtnEditSideViewPipelineActionListener());
	        col++;

	        panel.add(tfUseSideViewPipelineFrom = new JTextField(), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", fill, default");
	        col++;
	        
	        row++;
        }

        
        row = 6;
        col = 1;
        {
	        panel.add(new JLabel("Camera: "), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", right, default");
	        col++;

	        panel.add(bMoveToDropBox = new JButton("Move to DropBox"), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", right, default");
	        bMoveToDropBox.addActionListener(new BtnMoveToDropBoxActionListener());
	        col++;

	        panel.add(bMoveToSideView = new JButton("Move to Side View"), Integer.toString(col*2)+", "+ Integer.toString(row*2)+", right, default");
	        bMoveToSideView.addActionListener(new BtnMoveToSideViewActionListener());
	        col++;
	        
	        // TODO 4: add Button for set DropBox location
	        // TODO 4: add Button for set Side View location
	        row++;
        }

        
        
        JPanel warningPanel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) warningPanel.getLayout();
        contentPanel.add(warningPanel, 0);
        
        JLabel lblWarningThisFeeder = new JLabel("Warning: This feeder is incomplete and experimental. Use at your own risk.");
        lblWarningThisFeeder.setFont(new Font("Lucida Grande", Font.PLAIN, 16));
        lblWarningThisFeeder.setForeground(Color.RED);
        lblWarningThisFeeder.setHorizontalAlignment(SwingConstants.LEFT);
        warningPanel.add(lblWarningThisFeeder);
    }

    
    
    @Override
    public void createBindings() {
        super.createBindings();
        LengthConverter lengthConverter = new LengthConverter();
        IntegerConverter integerConverter = new IntegerConverter();
        DoubleConverter doubleConverter = new DoubleConverter(Configuration.get().getLengthDisplayFormat());
        
        // for reference see ReferenceTrayFeeder
        addWrappedBinding(feeder, "boxTrayId", 			tfboxTrayId, 			"text", integerConverter);
        addWrappedBinding(feeder, "subBoxName", 		tfSubBoxName, 			"text");
    	addWrappedBinding(feeder, "pumpName", 			tfPumpName, 			"text");
        addWrappedBinding(feeder, "valveName", 			tfValveName, 			"text");
        addWrappedBinding(feeder, "pressureSensorName", tfPressureSensorName, 	"text");
        addWrappedBinding(feeder, "pressureDelta", 		tfPressureDelta, 		"text", doubleConverter);
        addWrappedBinding(feeder, "maxZTravel", 		tfMaxZTravel, 			"text", lengthConverter);
        addWrappedBinding(feeder, "lastCatchZDepth", 	tfLastCatchZDepth, 		"text", lengthConverter);
        addWrappedBinding(feeder, "zStepOnPickup", 		tfZStepOnPickup, 		"text", lengthConverter);
        addWrappedBinding(feeder, "dwellOnZStep", 		tfDwellOnZStep, 		"text", integerConverter);
        
        addWrappedBinding(feeder, "useUpsideUpPipelineFrom", 		tfUseUpsideUpPipelineFrom, 		"text");
        addWrappedBinding(feeder, "useUpsideDownPipelineFrom", 		tfUseUpsideDownPipelineFrom, 	"text");
        addWrappedBinding(feeder, "useAnythingElsePipelineFrom", 	tfUseAnythingElsePipelineFrom, 	"text");
        addWrappedBinding(feeder, "useSideViewPipelineFrom", 		tfUseSideViewPipelineFrom, 		"text");
        
        addWrappedBinding(feeder, "upsideDownPipelineEnabledFlag", cbEnableUpsideDown, "selected");	
        addWrappedBinding(feeder, "sideViewPipelineEnabledFlag", cbEnableSideView, "selected");	


        // TODO 5: is this needed? (copy from trayfeeder)
//        ComponentDecorators.decorateWithAutoSelect(tfPumpName);
//        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldOffsetsX);
//        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldOffsetsY);
//
//        ComponentDecorators.decorateWithAutoSelect(textFieldTrayCountX);
//        ComponentDecorators.decorateWithAutoSelect(textFieldTrayCountY);
//        ComponentDecorators.decorateWithAutoSelect(textFieldFeedCount);
    }
    
    private void setDefaultLocation() {
    	feeder.setDefaultLocation(Integer.parseInt(tfboxTrayId.getText()), tfSubBoxName.getText());
    }
    

    
    private void editUpsideUpPipeline() throws Exception {
        CvPipeline pipeline = feeder.getUpsideUpPipeline();
        pipeline.setProperty("camera", Configuration.get().getMachine().getDefaultHead().getDefaultCamera()); // TODO 5: derive camera from feeder
        pipeline.setProperty("feeder", feeder);
        CvPipelineEditor editor = new CvPipelineEditor(pipeline);
        JDialog dialog = new JDialog(MainFrame.get(), feeder.getPart().getId() + "UpsideUp Pipeline");
        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(editor);
        dialog.setSize(1024, 768);
        dialog.setVisible(true);
    }

    private void editUpsideDownPipeline() throws Exception {
        CvPipeline pipeline = feeder.getUpsideDownPipeline();
        pipeline.setProperty("camera", Configuration.get().getMachine().getDefaultHead().getDefaultCamera()); // TODO 5: derive camera from feeder
        pipeline.setProperty("feeder", feeder);
        CvPipelineEditor editor = new CvPipelineEditor(pipeline);
        JDialog dialog = new JDialog(MainFrame.get(), feeder.getPart().getId() + "UpsideDown Pipeline");
        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(editor);
        dialog.setSize(1024, 768);
        dialog.setVisible(true);
    }

    private void editAnythingElsePipeline() throws Exception {
        CvPipeline pipeline = feeder.getAnythingElsePipeline();
        pipeline.setProperty("camera", Configuration.get().getMachine().getDefaultHead().getDefaultCamera()); // TODO 5: derive camera from feeder
        pipeline.setProperty("feeder", feeder);
        CvPipelineEditor editor = new CvPipelineEditor(pipeline);
        JDialog dialog = new JDialog(MainFrame.get(), feeder.getPart().getId() + "Anything Else Pipeline");
        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(editor);
        dialog.setSize(1024, 768);
        dialog.setVisible(true);
    }

    private void editSideViewPipeline() throws Exception {
        CvPipeline pipeline = feeder.getSideViewPipeline();
        pipeline.setProperty("camera", Configuration.get().getMachine().getDefaultHead().getDefaultCamera()); // TODO 5: derive camera from feeder
        pipeline.setProperty("feeder", feeder);
        CvPipelineEditor editor = new CvPipelineEditor(pipeline);
        JDialog dialog = new JDialog(MainFrame.get(), feeder.getPart().getId() + "Side View Pipeline");
        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(editor);
        dialog.setSize(1024, 768);
        dialog.setVisible(true);
    }

    
    // TODO 4: what about this exceptions?
    private void moveToDropBox() {
    	feeder.moveToDropBox();
    }
    
    private void moveToSideView(){
    	feeder.moveToSideView();
    }
    
    
    
    private class BtnSetDefaultLocationActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                setDefaultLocation();
            });
        }
    }

    
    private class BtnEditUpsideUpPipelineActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                editUpsideUpPipeline();
            });
        }
    }
    
    private class BtnEditUpsideDownPipelineActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                editUpsideDownPipeline();
            });
        }
    }
    
    private class BtnEditAnythingElsePipelineActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                editAnythingElsePipeline();
            });
        }
    }
    
    private class BtnEditSideViewPipelineActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                editSideViewPipeline();
            });
        }
    }
    
    
    private class BtnMoveToDropBoxActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                moveToDropBox();
            });
        }
    }
    
    
    private class BtnMoveToSideViewActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
            	moveToSideView();
            });
        }
    }    
 }
