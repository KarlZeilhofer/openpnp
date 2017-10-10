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
        						FormSpecs.DEFAULT_ROWSPEC,}));
        int row = 1;
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
	        JLabel lbl = new JLabel("Pump Name");
	        devPanel.add(lbl, "2, "+ Integer.toString(row*2)+", right, default");
	
	        JTextField tf = tfPumpName = new JTextField();
	        tf.setText("Pumpe");
	        devPanel.add(tf, "4, "+ Integer.toString(row*2)+", fill, default");
	        tf.setColumns(3);
	        
	        row++;
        }
        
        {
	        JLabel lbl = new JLabel("Valve Name");
	        devPanel.add(lbl, "2, "+ Integer.toString(row*2)+", right, default");
	
	        JTextField tf = tfValveName = new JTextField();
	        tf.setText("Ventil");
	        devPanel.add(tf, "4, "+ Integer.toString(row*2)+", fill, default");
	        tf.setColumns(3);
	        
	        row++;

        }
        
     
        {
	        JLabel lbl = new JLabel("Pressure Sensor Name");
	        devPanel.add(lbl, "2, "+ Integer.toString(row*2)+", right, default");
	
	        JTextField tf = tfPressureSensorName = new JTextField();
	        devPanel.add(tf, "4, "+ Integer.toString(row*2)+", fill, default");
	        tf.setColumns(3);
	        
	        row++;

        }
        
     
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
        panel.setBorder(new TitledBorder(null, "Vision", TitledBorder.LEADING, TitledBorder.TOP,
                null, null));
        contentPanel.add(panel);
        panel.setLayout(new FormLayout(new ColumnSpec[] {
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
                FormSpecs.DEFAULT_ROWSPEC,}));

        JButton btnEditPipeline = new JButton("Edit");
        btnEditPipeline.addActionListener(new BtnEditPipelineActionListener());
        
        JLabel lblFeedPipeline = new JLabel("Feed Pipeline");
        panel.add(lblFeedPipeline, "2, 2");
        panel.add(btnEditPipeline, "4, 2");

        JButton btnResetPipeline = new JButton("Reset");
        btnResetPipeline.addActionListener(new BtnResetPipelineActionListener());
        panel.add(btnResetPipeline, "6, 2");
        
        JLabel lblTrainingPipeline = new JLabel("Training Pipeline");
        panel.add(lblTrainingPipeline, "2, 4");
        
        JButton btnEditTrainingPipeline = new JButton("Edit");
        btnEditTrainingPipeline.addActionListener(new BtnEditTrainingPipelineActionListener());
        panel.add(btnEditTrainingPipeline, "4, 4");
        
        JButton btnResetTrainingPipeline = new JButton("Reset");
        btnResetTrainingPipeline.addActionListener(new BtnResetTrainingPipelineActionListener());
        panel.add(btnResetTrainingPipeline, "6, 4");
        
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



        // TODO 1: is this needed? (copy from trayfeeder)
//        ComponentDecorators.decorateWithAutoSelect(tfPumpName);
//        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldOffsetsX);
//        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldOffsetsY);
//
//        ComponentDecorators.decorateWithAutoSelect(textFieldTrayCountX);
//        ComponentDecorators.decorateWithAutoSelect(textFieldTrayCountY);
//        ComponentDecorators.decorateWithAutoSelect(textFieldFeedCount);
    }
    
    
    
    private void editPipeline() throws Exception {
        CvPipeline pipeline = feeder.getPipeline();
        pipeline.setProperty("camera", Configuration.get().getMachine().getDefaultHead().getDefaultCamera());
        pipeline.setProperty("feeder", feeder);
        CvPipelineEditor editor = new CvPipelineEditor(pipeline);
        JDialog dialog = new JDialog(MainFrame.get(), feeder.getPart().getId() + " Pipeline");
        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(editor);
        dialog.setSize(1024, 768);
        dialog.setVisible(true);
    }

    private void resetPipeline() {
        feeder.resetPipeline();
    }
    
    private void editTrainingPipeline() throws Exception {
        CvPipeline pipeline = feeder.getTrainingPipeline();
        pipeline.setProperty("camera", Configuration.get().getMachine().getDefaultHead().getDefaultCamera());
        pipeline.setProperty("feeder", feeder);
        CvPipelineEditor editor = new CvPipelineEditor(pipeline);
        JDialog dialog = new JDialog(MainFrame.get(), feeder.getPart().getId() + " Training Pipeline");
        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(editor);
        dialog.setSize(1024, 768);
        dialog.setVisible(true);
    }

    private void resetTrainingPipeline() {
        feeder.resetTrainingPipeline();
    }
    
    private class BtnEditTrainingPipelineActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                editTrainingPipeline();
            });
        }
    }
    private class BtnResetTrainingPipelineActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                resetTrainingPipeline();
            });
        }
    }
    private class BtnEditPipelineActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                editPipeline();
            });
        }
    }
    private class BtnResetPipelineActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                resetPipeline();
            });
        }
    }
}
