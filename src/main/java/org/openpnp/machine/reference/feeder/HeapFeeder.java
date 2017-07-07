/*
 * Copyright (C) 2017 Karl Zeilhofer <zeilhofer at team14.at>
 * based on the AdvancedLoosePartFeeder from Jason von Nieda <jason@vonnieda.org>, 2011
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

package org.openpnp.machine.reference.feeder;

import java.util.List;

import javax.swing.Action;

import org.apache.commons.io.IOUtils;
import org.opencv.core.RotatedRect;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.ReferenceFeeder;
import org.openpnp.machine.reference.feeder.wizards.HeapFeederConfigurationWizard;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.spi.PropertySheetHolder.PropertySheet;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.OpenCvUtils;
import org.openpnp.util.VisionUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.simpleframework.xml.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Karl Zeilhofer <zeilhofer at team14.at>
 * 
 * The HeapFeeder is a very advanced type of feeder, which makes it possible, to pick
 * up parts from a heap of loose parts of the same type. 
 * 
 * This enables the machine to have a 2D-array of different parts, instead of only a linear
 * set of feeder-tapes along the edge of the machine table. 
 *
 */

public class HeapFeeder extends ReferenceFeeder {
    private final static Logger logger = LoggerFactory.getLogger(AdvancedLoosePartFeeder.class);
	
    @Element(required = false)
    private CvPipeline pipeline = createDefaultPipeline();

    @Element(required = false)
    private CvPipeline trainingPipeline = createDefaultTrainingPipeline();
    
    // TODO 0: add more pipelines for the different stages:
    // TODO 0: add default pipelines
    @Element(required = false)
    private CvPipeline heapPipeline;

    @Element(required = false)
    private CvPipeline upcamPipeline;

    @Element(required = false)
    private CvPipeline separationAreaPipeline;

    @Element(required = false)
    private CvPipeline flipAreaPipeline;


    
    /**
     * The ReferenceFeeder.location is where the parts should be picked up from. 
     * 
     * The area is analyzed by the down-camera before picking up one, or 
     * accidently multiple parts. 
     */
    
    
    /**
     * The heapToUpcamWayPoints are points in the 3D space of the machine, where
     * the parts, which are picked up from the heap, should be transported along 
     * to the camera. 
     * 
     * This is important to avoid mixing up the parts on different heaps by accidently 
     * losing a part during transportation. 
     * 
     * For simplicity, on the way from the up-camera to the separation area, the chip flip area
     * and the pickLocation there must not be any heap underneath. 
     */
    private List<Location> heapToUpcamWayPoints;

    /**
     * The separationDropLocation is a fixed location shared by all feeders of this type. 
     * 
     * After picking up the parts from the heap, perhaps multiple parts are on 
     * the nozzle. This is detected by the up-camera. In this case, the parts
     * are dropped on the separationArea. 
     * 
     * The Z value is the height, from where the parts are dropped from. 
     * 
     * @see separationPickupArea
     */
    static private Location separationDropLocation;
    
    
    /**
     * The separationPickupArea is a fixed location shared by all feeders of this type. 
     * 
     * It is the location, where the down-camera looks for the dropped parts after 
     * separation. 
     * 
     * @see separationDropLocation
     */
    static private Location separationPickupArea = separationDropLocation;
    
    
    /**
     * The chipFlipDropLocation is a fixed location shared by all feeders of this type. 
     * 
     * After picking up a part from the heap or from the separationPickupArea, 
     * the part is perahps flipped, so the bottom side of the part is upwards. 
     * 
     * The Z value is the height, from where the part is dropped from. 
     * 
     * @see chipFlipPickupArea
     */
    static private Location chipFlipDropLocation;

    /**
     * The chipFlipPickupArea is a fixed location shared by all feeders of this type. 
     * 
     * After dropping the part from the chipFlipDropLocation, it must be within the 
     * field of view of the down-camera to be recognized. 
     * 
     * @see chipFlipDropLocation
     */
    static private Location chipFlipPickupArea;
    
    /**
     * The pickLocation is a fixed location for this feeder. 
     * The part is placed there by the feeder when all necessary operations 
     * have been passed before. 
     */
    private Location pickLocation;

    

	@Override
	public Location getPickLocation() throws Exception {
		return pickLocation == null ? location : pickLocation;
	}

    @Override
    public void feed(Nozzle nozzle) throws Exception {
    	
        Camera camera = nozzle.getHead().getDefaultCamera();
        // Move to the feeder pick location
        MovableUtils.moveToLocationAtSafeZ(camera, location);
        
        
    	// TODO 0: implement complete sequence of operations!
    	
        for (int i = 0; i < 3; i++) {
            pickLocation = getPickLocation(camera, nozzle);
            camera.moveTo(pickLocation);
        }
    }

    private Location getPickLocation(Camera camera, Nozzle nozzle) throws Exception {
        // Process the pipeline to extract RotatedRect results
        pipeline.setProperty("camera", camera);
        pipeline.setProperty("nozzle", nozzle);
        pipeline.setProperty("feeder", this);
        pipeline.process();
        // Grab the results
        List<RotatedRect> results = (List<RotatedRect>) pipeline.getResult("results").model;
        if (results.isEmpty()) {
            throw new Exception("Feeder " + getName() + ": No parts found.");
        }
        // Find the closest result
        results.sort((a, b) -> {
            Double da = VisionUtils.getPixelLocation(camera, a.center.x, a.center.y)
                    .getLinearDistanceTo(camera.getLocation());
            Double db = VisionUtils.getPixelLocation(camera, b.center.x, b.center.y)
                    .getLinearDistanceTo(camera.getLocation());
            return da.compareTo(db);
        });
        RotatedRect result = results.get(0);
        Location location = VisionUtils.getPixelLocation(camera, result.center.x, result.center.y);
        // Get the result's Location
        // Update the location with the result's rotation
        location = location.derive(null, null, null, -(result.angle + getLocation().getRotation()));
        // Update the location with the correct Z, which is the configured Location's Z
        // plus the part height.
        location =
                location.derive(null, null,
                        this.location.convertToUnits(location.getUnits()).getZ()
                                + part.getHeight().convertToUnits(location.getUnits()).getValue(),
                        null);
        MainFrame.get().getCameraViews().getCameraView(camera)
                .showFilteredImage(OpenCvUtils.toBufferedImage(pipeline.getWorkingImage()), 250);
        return location;
    }

    public CvPipeline getPipeline() {
        return pipeline;
    }

    public void resetPipeline() {
        pipeline = createDefaultPipeline();
    }

    public CvPipeline getTrainingPipeline() {
        return trainingPipeline;
    }

    public void resetTrainingPipeline() {
        trainingPipeline = createDefaultTrainingPipeline();
    }

	@Override
	public Wizard getConfigurationWizard() {
		return new HeapFeederConfigurationWizard(this);
	}

	@Override
	public String getPropertySheetHolderTitle() {
		return getClass().getSimpleName() + " " + getName();
	}

	@Override
	public PropertySheetHolder[] getChildPropertySheetHolders() {
		// TODO Auto-generated method stub
		return null;
	}
	
    @Override
    public PropertySheet[] getPropertySheets() {
        return new PropertySheet[] {new PropertySheetWizardAdapter(getConfigurationWizard())};
    }

	@Override
	public Action[] getPropertySheetHolderActions() {
		// TODO Auto-generated method stub
		return null;
	}

	
    public static CvPipeline createDefaultPipeline() {
        try {
            String xml = IOUtils.toString(HeapFeeder.class
                    .getResource("HeapFeeder-DefaultPipeline.xml"));
            return new CvPipeline(xml);
        }
        catch (Exception e) {
            throw new Error(e);
        }
    }
    
    public static CvPipeline createDefaultTrainingPipeline() {
        try {
            String xml = IOUtils.toString(HeapFeeder.class
                    .getResource("HeapFeeder-DefaultTrainingPipeline.xml"));
            return new CvPipeline(xml);
        }
        catch (Exception e) {
            throw new Error(e);
        }
    }
}
