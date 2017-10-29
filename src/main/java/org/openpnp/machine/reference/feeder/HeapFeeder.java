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

import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;

import org.apache.commons.io.IOUtils;
import org.opencv.core.RotatedRect;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.ReferenceActuator;
import org.openpnp.machine.reference.ReferenceCamera;
import org.openpnp.machine.reference.ReferenceDriver;
import org.openpnp.machine.reference.ReferenceFeeder;
import org.openpnp.machine.reference.ReferenceHeadMountable;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzle;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.driver.GcodeDriver;
import org.openpnp.machine.reference.feeder.wizards.HeapFeederConfigurationWizard;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.OpenCvUtils;
import org.openpnp.util.VisionUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.core.Commit;
import org.pmw.tinylog.Logger;


/**
 * 
 * @author Karl Zeilhofer <zeilhofer |at| team14 dot at>
 * 

The HeapFeeder is a very advanced type of feeder, which makes it possible, to pick
up parts from a heap of loose parts of the same type. 

This enables the machine to have a 2D-array of different parts, instead of only a linear
set of feeder-tapes along the edge of the machine table. 

Definitions and Conventions: 

## BoxTray

Typically a heap is filled into a small Box, which is part of a BoxTray. 
Each Box is by definition surrounded by corridors for parts-transportation
(typically 7mm). The horizontal section of a Box is a square, oriented along the x and y axis. 
Its inner edges are typically 12mm. The whole BoxTray consists of Nx by Ny Boxes.
Between the Boxes are corridors, but for saving space, only along the y-direction and
also the boxes are grouped into a pair of columns. 



	BoxTray Geometry
	
	y-axis
	^
	|
	       A     B         C     D
	+-----------------------------------+
	|                                   |
	|   +-----+-----+   +-----+-----+   |
	|   |     |     | c |     |     |   | 4    
	|   |     |     | o |     |     |   |     
	|   +-----+-----+ r +-----+-----+   |
	|   |     |     | r |     |     |   | 3    
	|   |     |     | i |     |     |   |     
	|   +-----+-----+ d +-----+-----+   |
	|   |     |     | o |     |     |   | 2    
	|   |     |     | r |     |     |   |     
	|   +-----+-----+   +-----+-----+   |
	|   |     |     |   |     |     |   | 1    
	|   |     |     |   |     |     |   |     
	|   +-----+-----+   +-----+-----+   |
	|            c o r r i d o r        |
   (0)----------------------------------+  --> x-axis
	origin
		
Our BoxTrays for testing have 2 columns and 8 Rows (A1..B8). 

The nozzle takes the parts only from the center of one Box, since there is 
only very little clearance for the nozzles collar (12mmx12mm vs. 10.5mm diameter). 
Minor xy movement is added to mix them up a bit. Otherwise we dig hole in the center
and push the parts then very hard. 


Conventions to the CV Pipelines:

UpsideUp:
	must detect only parts, which have their upside upwards. 
	They are ready for placement (with optional bottom vision)
	The ROI (region of interest) should be clipped to the floor of the 
	DropBox. A sanity check is done anyway for the nozzle movement before
	picking the parts up. 

UpsideDown:
	must return all parts which have to be flipped before placement. 
	It should return also the upside up parts with a very high true positives rate
	and alow false negative rate. This leads to a speed improvement: 
	When we process the UpsideUp PL, we also process the Upside Down, to let
	the feeder know, if we have to fetch new parts from the Box or if we could expect
	flipped parts in the DropBox. 
	The ROI must be clipped again to the diameter of the floor 
	of the DropBox. 

AnythingElse:
	The ROI is expanded, so we can detect something also on the slopes of the
	DropBox. It must return everything, that doesn't belong to the DropBox itself. 
	
SideView:
	This vision is used to detect flipped SOT23 for example. From the top view
	upside up and upside down parts are nearly not distinguishable - even for 
	the human eye. 
	The parts are brought to the chip flipper, where we also have a mirror directed to 
	the put location. The horizontal centerline of the image must be aligned with
	the vertical center of the part under test. It must return rotated rects for
	the bright pins, which normally should only appear above or below the centerline
	of the image. No returns or mixed returns lead to an exception. 



 */

public class HeapFeeder extends ReferenceFeeder {
    
    
    @Attribute(required = false)
    private String pumpName = "Pumpe"; // TODO 4: parameter
    @Attribute(required = false)
    private String valveName = "Ventil"; // TODO 4: parameter
    @Attribute(required = false)
    private String pressureSensorName = "Drucksensor"; // TODO 4: parameter
    @Attribute(required = false)
    private double pressureDelta = 3.0;
    @Element(required = false)
    private Length maxZTravel = new Length(-2.0, LengthUnit.Millimeters); // length unit
    @Element(required = false)
    private Length zStepOnPickup = new Length(-0.2, LengthUnit.Millimeters); // length unit
    @Attribute(required = false)
    private int dwellOnZStep = 0; // needed for sensor stabilization

    @Element(required = false)
    private Length lastCatchZDepth = new Length(0, LengthUnit.Millimeters); // remember the top level of the heap (relative to location.z)

    
    
    @Element(required = false)
    private Length corridorWidth = new Length(7, LengthUnit.Millimeters); // TODO 5: make adjustable
    @Element(required = false)
    private Length boxTrayWallThickness = new Length(1, LengthUnit.Millimeters); // TODO 5: make adjustable
    @Element(required = false)
    private Length boxTrayInnerSizeX = new Length(12, LengthUnit.Millimeters); // TODO 5: make adjustable
    @Element(required = false)
    private Length boxTrayInnerSizeY = new Length(12, LengthUnit.Millimeters); // TODO 5: make adjustable

    @Attribute(required = false)
    private String subBoxName = "A1"; 
	@Attribute(required = false)
    private int boxTrayId = 1; 
	
	@Attribute(required = false)
    private int zSwitchActivationCounter = 0;  // used for statistics
    
	// @ElementList(required = false) // TODO 4: fix bug with Megabytes of repeated entries in machine.xml
    private static List<Location> globalBoxTrayLocations = new ArrayList<Location>(); // TODO 5: provide GUI

   
    @Element(required = false)
    private CvPipeline upsideUpPipeline = createDefaultPipeline();
    @Attribute(required = false)	   
    private String useUpsideUpPipelineFrom = new String(); // if this string is empty, we use our own pipeline. 

    @Element(required = false)
    private CvPipeline upsideDownPipeline = createDefaultPipeline();
    @Attribute(required = false)
    private String useUpsideDownPipelineFrom = new String(); // if this string is empty, we use our own pipeline. 
    @Attribute(required = false)
    private boolean upsideDownPipelineEnabledFlag = true;


	@Element(required = false)
    private CvPipeline anythingElsePipeline = createDefaultPipeline();
    @Attribute(required = false)
    private String useAnythingElsePipelineFrom = new String(); // if this string is empty, we use our own pipeline. 

    @Element(required = false)
    private CvPipeline sideViewPipeline = createDefaultPipeline();
    @Attribute(required = false)
    private String useSideViewPipelineFrom = new String(); // if this string is empty, we use our own pipeline. 
    @Attribute(required = false)
    private boolean sideViewPipelineEnabledFlag = false;
    @Attribute(required = false)
    private boolean useDropBoxFlipper = true; // TODO 3: GUI
    @Attribute(required = false)
    private boolean useChipFlipper = false; // TODO 3: GUI 
 
   
    public boolean isUpsideDownPipelineEnabledFlag() {
		return upsideDownPipelineEnabledFlag;
	}

	public void setUpsideDownPipelineEnabledFlag(boolean upsideDownPipelineEnabledFlag) {
		this.upsideDownPipelineEnabledFlag = upsideDownPipelineEnabledFlag;
	}

	public boolean isSideViewPipelineEnabledFlag() {
		return sideViewPipelineEnabledFlag;
	}

	public void setSideViewPipelineEnabledFlag(boolean sideViewPipelineEnabledFlag) {
		this.sideViewPipelineEnabledFlag = sideViewPipelineEnabledFlag;
	}


    
    /**
     * The ReferenceFeeder.location is where the parts should be picked up from. 
     * 
     * In the HeapFeeder this is the center of a single Box. The height of this 
     * location is the top edge of the Box. It is assumed, that a box is at max. 
     * filled until the top edege, without building a heap that would extend the
     * Box's height.  
     * 
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
     * and the pickLocation there must not be any Box underneath. 
     */
    private ArrayList<Location> heapToExitWayPoints = new ArrayList<Location>(); // TODO 4: use this and implement in GUI or make it very intelligent. 
    // NOTE: for the prototype, we use a direct path until to the right edge of box tray Nr 1.   

    /**
     * The dropBoxLocation is a fixed location shared by all feeders of this type. 
     * 
     * It is the center, top point of the dropbox. 
     * 
     * After picking up the parts from the heap, perhaps multiple parts are on 
     * the nozzle. 
     * For now, every time the parts are dropped here (for separation). 
     * The DropBox has a flat area of 15x15mm, small enough that the camera can 
     * analyze it for parts. 

     * TODO 3: Optional Bottom Vision Priority 
     * This is detected by the up-camera. Only in this case, the parts
     * are dropped on the separationArea. 
     * 
     * 
     */
 // TODO 4: dropBoxLocation attribute
    private Location dropBoxTopLocation() throws Exception{
    	ReferenceFeeder f = (ReferenceFeeder)(getMachine().getFeederByName("position-dropbox-top-center"));
    	
    	if(f == null) {
    		throw new Exception("Missing Feeder position-dropbox-floor for position of DropBox");
    	}
    	
    	return f.getLocation();
    }

    
    /**
     * The dropBoxPlaneLocation is a fixed location shared by all feeders of this type. 
     * 
     * It is the center of the flat area of the DropBox. It is used for the built in 
     * AdvancedLoosePartFeeder, which is  responsible for picking the parts up again. 
     */
 // TODO 4: dropBoxFloorLocation attribute
    private Location dropBoxFloorLocation() throws Exception{
    	ReferenceFeeder f = (ReferenceFeeder)(getMachine().getFeederByName("position-dropbox-floor"));
    	
    	if(f == null) {
    		throw new Exception("Missing Feeder position-dropbox-floor for position of DropBox");
    	}
    	
    	return f.getLocation();
    }

    
    
    /**
     * The chipFlipPutLocation is a fixed location shared by all feeders of this type. 
     * 
     * After picking up a part from the heap or from the separationPickupArea, 
     * the part is perahps flipped, so the bottom side of the part is upwards. 
     * 
     * The Z value is the height of the chip flipper's put floor. 
     * 
     * @see chipFlipGetLocation
     */
    private Location chipFlipperPutLocation() throws Exception {
    	ReferenceFeeder f = (ReferenceFeeder)(getMachine().getFeederByName("position-chipflipper-1-put"));
    	
    	if(f == null) {
    		throw new Exception("Missing Feeder position-chipflipper-3-get for position of ChipFlipper's get surface");
    	}
    	
    	return f.getLocation();
    }

    /**
     * The chipFlipGetLocation is a fixed location shared by all feeders of this type. 
     * 
     * After flipping the chip by the chip flipper, it is fetched from here. 
     * We assume a static location, so no vision is used here.
     * 
     * The z-value is the surface where the parts lays on. 
     * 
     * @see chipFlipPutLocation
     */
    private Location chipFlipperGetLocation() throws Exception {
    	ReferenceFeeder f = (ReferenceFeeder)(getMachine().getFeederByName("position-chipflipper-3-get"));
    	
    	if(f == null) {
    		throw new Exception("Missing Feeder position-chipflipper-3-get for position of ChipFlipper's get surface");
    	}
    	
    	return f.getLocation();
    	
    }
    
    
    
    
    private Location sideViewLocation() throws Exception { // TODO 4: put into XML
    	ReferenceFeeder f = (ReferenceFeeder)(getMachine().getFeederByName("position-chipflipper-2-mirror"));
    	
    	if(f == null) {
    		throw new Exception("Missing Feeder position-chipflipper-2-mirror for position of DropBox");
    	}
    	
    	return f.getLocation();
    }
    
    /**
     * The pickLocation is a fixed location for this feeder object. 
     * The part is placed there by the feeder when all necessary operations 
     * have been passed before. 
     */
    private Location pickLocation = null;
    
    
    @Attribute(required = false)
    static private int currentUpsideUpPartsInDropBox=-1; // -1 means unknown
    @Attribute(required = false)
    static private int currentUpsideDownPartsInDropBox=-1; // -1 means unknown
    @Attribute(required = false)
    static private int currentAnythingElseCountInDropBox=-1; // -1 means unknown
    @Attribute(required = false)
    static private String mostRecentHeapFeederId=""; // this is used accross the instances
    	// and belongs to the values above. 

    
    // constructor
    public HeapFeeder()
    {
    	Logger.trace("Constructor: this.name = " + name); /// see afterXmlInit()
    }
    
    /**
     * This method is called after initialization of all the Attributes and Elements
     */
    @Commit
    public void afterXmlInit() {
    	// TODO 5: move this into the GUI!
    	if(globalBoxTrayLocations.isEmpty()) {
	    	globalBoxTrayLocations.add(0, new Location(LengthUnit.Millimeters, 0,0,0,0)); // dummy
	    	globalBoxTrayLocations.add(1, new Location(LengthUnit.Millimeters, 266+3*34,168,-7,0));
	    	globalBoxTrayLocations.add(2, new Location(LengthUnit.Millimeters, 266+2*34,168,-7,0));
	    	globalBoxTrayLocations.add(3, new Location(LengthUnit.Millimeters, 266+1*34,168,-7,0));
	    	globalBoxTrayLocations.add(4, new Location(LengthUnit.Millimeters, 266+0*34,168,-7,0));
    	}
    	
    	Logger.trace("@Commit: this.name = " + name);   	
    }
    

	@Override
	public Location getPickLocation() throws Exception {
		return pickLocation == null ? location : pickLocation;
	}

    @Override
    public void feed(Nozzle nozzle) throws Exception {
        pickLocation = null;
    	
        Camera camera = nozzle.getHead().getDefaultCamera();
        
        Logger.info(getName() + ": feed() with these counts: normal = " + Integer.toString(currentUpsideUpPartsInDropBox) + 
        		", flipped = " + Integer.toString(currentUpsideDownPartsInDropBox) + 
        		", anything else = " + Integer.toString(currentAnythingElseCountInDropBox) + 
        		", recent Feeder: " + getMachine().getFeeder(mostRecentHeapFeederId).getName());

        double retries = 3;
        while(mostRecentHeapFeederId.compareTo(this.getId()) != 0 // if other heapfeeder has been active
        		&&
        		(currentUpsideUpPartsInDropBox != 0 || 
        		 currentUpsideDownPartsInDropBox !=0 ||
        		 currentAnythingElseCountInDropBox !=0)
        		 && retries >= 0) {
        	
        	HeapFeeder otherFeeder = (HeapFeeder) getMachine().getFeeder(mostRecentHeapFeederId);
        	
        	Logger.info(getName() + ": triggers cleanUp() on feeder " + getMachine().getFeeder(mostRecentHeapFeederId).getName());
        	otherFeeder.cleanUp(nozzle, this);
        	retries--;
        }
        if(retries < 0) {
        	throw new Exception(getName() + ": failed to clean up");
        }
        
    	
        retries = 7;
		do {
			checkForCleanNozzleTip(nozzle);
			
			if(currentUpsideUpPartsInDropBox <= 0 && currentUpsideDownPartsInDropBox <= 0) {
				pickNewPartFromBox(nozzle);
				transportToExit(nozzle);
				moveToDropLocationAndDrop(nozzle);
			}else {
				dummyMove(nozzle); // TODO cleanup dummy move
			}

			Location lNormal=null;
			Location lFlipped=null;
			Location lPick=null;
			//int recentUpsideUpPartsInDropBox = currentUpsideUpPartsInDropBox;
			
			lNormal = getNextUpsideUpLocationInDropbox(camera, nozzle);
			if(upsideDownPipelineEnabledFlag == true 
					&& currentUpsideUpPartsInDropBox <= 2) { // bother the upside down pipeline only if it is the 
						// last normal part we are taking now, or we didn't find an upside up part any more. 
				
				// call this also to update the number of flipped chips. 
				// so we can avoid a fetch in the next cycle, if we have flipped chips. 
				lFlipped = getNextUpsideDownLocationInDropbox(camera, nozzle); 
			}
			

			if (lNormal != null) {
				lPick = lNormal;
			}else if(lFlipped != null) {
				lPick = lFlipped;
			}
			
			if(lPick != null) {
				// sanity check: location must be within 7mm radius of the center of the dropbox
				if(lPick.getLinearDistanceTo(dropBoxTopLocation()) > 7.5) { // TODO 4: set attribute for dropbox radius
					throw new Exception("Pick location for upside up parts is out of the floor of the DropBox. "+
							"Check coordinates and/or vision pipeline");
				}else if(lPick.getLinearDistanceTo(dropBoxTopLocation()) > 7) { // TODO 4: set attribute for dropbox radius
					//limit the radius to 7mm
					Location c = dropBoxTopLocation();
					double r = lPick.getLinearDistanceTo(dropBoxTopLocation());
					
					Location delta = lPick.subtract(c);
					delta = delta.multiply(1/r, 1/r, 0, 0).multiply(7,  7,  0,  0); // TODO replace 7
					
					Location clipped = c.add(delta);
					lPick = clipped;
				}
				lPick = lPick.derive(null, null, dropBoxFloorLocation().getZ() + part.getHeight().getValue(), null);
			}
			
			// now we know, where to pick from, but should we flip or do a side vision before exit?
			if(lNormal != null) {
				if(sideViewPipelineEnabledFlag == false) {
					pickLocation = lPick; // finished
				}else {
					// TODO 2: implement side view cycle
				}
				currentUpsideUpPartsInDropBox--;
			}else if(lFlipped != null) {
				// then we have to do the flipping before
				Logger.info(getName() + ": Flip part from DropBox, retries = " + Double.toString(retries));
				
				pickPart(nozzle, lPick);
				if(useChipFlipper) { // TODO 3: optimize this cycle - not very reliable
					transportToChipFlipper(nozzle);
					flip();
					pickLocation = chipFlipperGetLocation().add(
							new Location(LengthUnit.Millimeters, 0.0,0.0,getPart().getHeight().getValue()-0.5, 0.0)); // TODO 1: optimize value   
							// TODO 4: constant
				}else if(useDropBoxFlipper) {
					slipOffInDropBox(nozzle);
					retries += 0.5; // try it more often with the DropBoxFlipper
				}
			}

			retries--;
		} while (pickLocation == null && retries > 0);
		
		if(retries <= 0) {
			throw new Exception("HeapFeeder failed to feed a new part after many retries");
		}
        
        
        
        // TOOD 0: use chip flipper
    }
    
	private void slipOffInDropBox(Nozzle nozzle) throws Exception{
		Logger.info(getName() + ": Slip off in DropBox");
		directMoveToAtSafeZ(nozzle, dropBoxTopLocation());

		valveOff(nozzle);
		directMoveTo(nozzle, dropBoxTopLocation().add(
				new Location(LengthUnit.Millimeters, -15, 0,0,0))); // TODO 4: constant
		nozzle.moveToSafeZ();
	}

	private void transportToChipFlipper(Nozzle nozzle) throws Exception {
		Logger.info(getName() + ": transport to ChipFlipper");
		nozzle.moveToSafeZ();
		double saveZ = nozzle.getLocation().getZ();
		
		directMoveTo(nozzle, chipFlipperPutLocation().derive(null,  null,  saveZ, null));
		
		double h = getPart().getHeight().getValue();
		if(h < 0.6) {
			h = 0.6; // we have to slip off the part against an obstacle, which is 0.5mm high
			// TODO 4: replace constant
		}
		
		directMoveTo(nozzle, chipFlipperPutLocation().add(
				new Location(LengthUnit.Millimeters, 0,0, h, 0)));
		
		valveOff(nozzle);
		
		directMoveTo(nozzle, nozzle.getLocation().add(
				new Location(LengthUnit.Millimeters, 0, 5, 0, 0)), 0.005); // TODO 4: replace this constant
		
		nozzle.moveToSafeZ(); // finished	
		
		// move to park position (out of the way of the chip flippers motion volume)
		directMoveTo(nozzle, nozzle.getLocation().add(
				new Location(LengthUnit.Millimeters, 40, 0, 0, 0))); // TODO 4: replace this constant
	}

	public void cleanUp(Nozzle nozzle, HeapFeeder calledBy) throws Exception{
		Logger.info(getName() + ": Cleaning up parts from feeder " + getName() + ", called by HeapFeeder" + calledBy.getName());
		
    	Camera camera = nozzle.getHead().getDefaultCamera();
    	currentUpsideUpPartsInDropBox = 0;
    	currentUpsideDownPartsInDropBox = 0;
 
    	// direct clean up, when part is already on the nozzle
		if(nozzle.getPart() != null && nozzle.getPart().getId() == this.getPart().getId()) {
			transportToBox(nozzle);
			valveOff(nozzle);			
			// slip off:
			double dX = boxTrayInnerSizeX.getValue()/2 + 2; // default to right exit
			if(boxColumn() == 0) { // left exit
				dX *= -1; 
			}
			directMoveTo(nozzle, location);
			directMoveTo(nozzle, location.add(new Location(LengthUnit.Millimeters, dX, 0, 0, 0)));
		}
		
		int counter = 0;
    	do {
    		checkForCleanNozzleTip(nozzle);

    		dummyMove(nozzle); // TODO 2: speed improvement

    		Location l;
    		l = getNextAnythingElseLocationInDropbox(camera, nozzle);
    		if (l != null) {
				// sanity check: location must be within 7mm radius of the center of the dropbox
				if(l.getLinearDistanceTo(dropBoxTopLocation()) > 7.5) { // TODO 4: set attribute for dropbox radius
    				throw new Exception("Cleaning up parts from feeder " + getName() + ", called by HeapFeeder" + calledBy.getName() + ": Pick location for Anything Else parts is out of the floor of the DropBox. "+
    						"Check coordinates and/or vision pipeline");
				}else if(l.getLinearDistanceTo(dropBoxTopLocation()) > 7) { // TODO 4: set attribute for dropbox radius
					//limit the radius to 7mm
					Location c = dropBoxTopLocation();
					double r = l.getLinearDistanceTo(dropBoxTopLocation());
					
					Location delta = l.subtract(c);
					delta = delta.multiply(1/r, 1/r, 0, 0).multiply(7,  7,  0,  0); // TODO replace 7
					
					Location clipped = c.add(delta);
					l = clipped;
				}

    			Location myPickLocation = l.derive(null, null, dropBoxFloorLocation().getZ() + part.getHeight().getValue(), null);

    			pickPart(nozzle, myPickLocation);
    			transportToBox(nozzle);
    			valveOff(nozzle);
    			
    			// slip off
    			double dX = boxTrayInnerSizeX.getValue()/2 + 2; // default to right exit
    			if(boxColumn() == 0) { // left exit
    				dX *= -1; 
    			}
    			directMoveTo(nozzle, location);
    			directMoveTo(nozzle, location.add(new Location(LengthUnit.Millimeters, dX, 0, 0, 0)));
    		}
    		counter++;
    		if(counter >= 10) { // TODO 4: constants
    			throw new Exception("Gave up on cleanup! Please check the DropBox!");
    		}
    	} while(currentAnythingElseCountInDropBox != 0);
    }

	private boolean checkForCleanNozzleTip(Nozzle nozzle) throws Exception {
		return checkForCleanNozzleTip(nozzle, true);
	}

	private boolean checkForCleanNozzleTip(Nozzle nozzle, boolean doThrow) throws Exception {
		Logger.info(getName() + ": Check for clean nozzle.");
		
    	pumpOn();
    	valveOn(nozzle);
    	nozzle.moveToSafeZ();
    	
    	final int limit = 500; // equals about 5s
    	double p0;
    	int retries=0;
    	while(retries < limit) {
        	p0 = pressure(nozzle);
        	ReferenceNozzleTip rn = (ReferenceNozzleTip) nozzle.getNozzleTip();
        	
        	if(p0 < rn.getVacuumLevelPartOff()) { // TODO 5: pressure logic
        		return true;
        	}   		
        	retries++;
    	}
    	
    	if(doThrow) {
    		throw new Exception("Nozzle tip pressure too high. Clean nozzle tip expected!");
    	}
    	return false;
	}

	/**
	 * Pick a part (internal method)
	 * Z-level must be the top surface of the part to be picked up. 
	 * @param nozzle
	 * @param pickLocation
	 */
	private void pickPart(Nozzle nozzle, Location pickLocation) throws Exception {
		nozzle.moveToSafeZ();
		double saveZ = nozzle.getLocation().getZ();
		
		directMoveTo(nozzle, pickLocation.derive(null, null, saveZ, null));
		directMoveTo(nozzle, pickLocation);
		
		valveOn(nozzle);
		
		nozzle.moveToSafeZ();
	}
	
	private void transportToExit(Nozzle nozzle) throws Exception {
		Logger.info(getName() + ": Transport to Exit");
    	nozzle.moveToSafeZ();
    	updateHeapToExitWayPoints();
    	for(Location loc : heapToExitWayPoints) {
    		directMoveTo(nozzle, loc);
    	}
	}
	
	private void transportToBox(Nozzle nozzle) throws Exception {
    	nozzle.moveToSafeZ();
    	double saveZ = nozzle.getLocation().getZ();
    	
    	updateHeapToExitWayPoints();
    	int count = heapToExitWayPoints.size();
    	
    	directMoveTo(nozzle, heapToExitWayPoints.get(count-1).derive(null, null, saveZ, null));
    	for(int i=count-1; i>=0; i--) {
    		directMoveTo(nozzle, heapToExitWayPoints.get(i));
    	}
	}

	private void updateHeapToExitWayPoints() throws Exception {
		heapToExitWayPoints.clear();
		
    	double saveZ = 0;
    	// TODO 4: double saveZ = nozzle.getLocation().getZ()
    	
    	Location nozLoc = location.derive(null, null, saveZ, null); // TODO 4: we assume here a save-z of 0
    	

    	heapToExitWayPoints.add(nozLoc);
    	
    	double dX = 0;
    	double dY=0;
    	double dZ=0;
    	
    	// for absolute coordinates we use Location.derive, which ignores a null-Value
    	Double x;
    	Double y;
    	Double z;
    	
    	if(boxColumn() == 0) { // exit left
    		dX = -boxTrayInnerSizeX.getValue()/2 - boxTrayWallThickness.getValue() - corridorWidth.getValue()/2;
    	}else{ // exit right
    		dX = +boxTrayInnerSizeX.getValue()/2 + boxTrayWallThickness.getValue() + corridorWidth.getValue()/2;
    	}
		dY = 0; 
		dZ = 0; // dont change
		nozLoc = nozLoc.add(new Location(LengthUnit.Millimeters, dX,dY,dZ,0));

		heapToExitWayPoints.add(nozLoc);
		
		// * move down into the corridor
		x = null; 
		y = null;
		z = location.getZ() -5; // TODO 4: we assume here a save-z of 0
		nozLoc = nozLoc.derive(x, y, z, null);

		heapToExitWayPoints.add(nozLoc);
		
		
		// * move towards front (near the origin of the current BoxTray) in the y-corridor
		dX=0;
		dY= -boxRow()*(boxTrayInnerSizeY.getValue() + boxTrayWallThickness.getValue()) 
				-boxTrayInnerSizeY.getValue()/2 -boxTrayWallThickness.getValue() 
				-corridorWidth.getValue()/2
				-1 ; // TODO 4: remove this workaround (locations with rotation needed)
		dZ=0;
		nozLoc = nozLoc.add(new Location(LengthUnit.Millimeters, dX,dY,dZ,0));

		heapToExitWayPoints.add(nozLoc);
		
		// * move to the right to the exit of the x-corridor
		x = globalBoxTrayLocations.get(1).getX() + 
				(3*boxTrayWallThickness.getValue() + 2*boxTrayInnerSizeX.getValue() + corridorWidth.getValue());
		y=null; 
		z=null; // dont change
		nozLoc = nozLoc.derive(x, y, z, null);

		heapToExitWayPoints.add(nozLoc);
    }
    
    private void moveToDropLocationAndDrop(Nozzle nozzle) throws Exception {
    	Logger.info(getName() + ": Transport to DropBox and slip off");
    	mostRecentHeapFeederId = getId();
    	
    	slipOffInDropBox(nozzle);
    	return; 
    }
 
    // TODO 3: cleanup 
    private void dummyMove(Nozzle nozzle) throws Exception {
    	mostRecentHeapFeederId = getId();
		valveOff(nozzle);
    }
    
    /**
     * We assume that the DropBox is cleaned up or has only parts from this feeder (only Anything Else)
     * We do not pick new Parts, if in the last top vision flipped parts were detected. 
     * @param camera
     * @param nozzle
     * @return
     * @throws Exception
     */
	private void pickNewPartFromBox(Nozzle nozzle) throws Exception {
        // Turn on the vacuum pump and the valve
    	
    	Logger.info(getName() + ": Picking new Part for Feeder " + getName() + " from Box" + subBoxName);
    	
    	pumpOn();
    	valveOn(nozzle);
       	
    	directMoveToAtSafeZ(nozzle, location); // center of our box
    	
        // * measure pressure (with no part on nozzle), until it has stabilized
    	//Thread.sleep(300); // TODO 4: obsolete?
    	
    	
    	// * move down until pressure rises significantly
    	// retry up to 5 times
    	double p0,p1;
    	double dZ = 0;
    	int stirDir=0; // 0 = (r,r), 1=(-r,r), 2=(-r,-r), 3=(r,-r)
    	boolean zSwitchWasActivated = false;
		
    	int retries = 5+1;
    	do {
        	p0 = pressure(nozzle); // TODO 4: implement stabilizations algorithm
        	//dZ = -maxZTravel.getValue(); // start some millimeters above lastCatchZDepth
        	dZ = +1;
        	
	    	// Start with motion at last catch height + 1mm (improves speed)
			double r = 0.5; // radius of stiring-motion, // TODO 4: parameter
			Location lStart = location.add(new Location(LengthUnit.Millimeters, 0,0,lastCatchZDepth.getValue()+dZ, 0.0));
			
	
			p1 = p0;
			int emptyBoxAlarmCounter = 0;
			
			zSwitchWasActivated = false;
			while(p1 - p0 < pressureDelta && dZ > maxZTravel.getValue() && 
					zSwitchWasActivated == false) {
				Location l = location;
		    	
				// TODO 2: handle collision with Box's floor
				double dX = 0;
				double dY = 0;
				
				if(stirDir>=4) {
					stirDir=0;
				}
				// stiring states:
				switch(stirDir) {
				case 0: 
					dX = r; 
					dY = 0; 
					break;
				case 1: 
					dX = 0; 
					dY = r; 
					break;
				case 2: 
					dX = -r; 
					dY = 0; 
					break;
				case 3: 
					dX = 0; 
					dY = -r; 
					break;
				}
				stirDir++;
				
				
				Location dLocation = new Location(LengthUnit.Millimeters, dX, dY, dZ, 0);
				l = lStart.add(dLocation);
				
				directMoveTo(nozzle, l);
				
				if(nozzle.getLocation().getZ() < (-42.7-1+1+getPart().getHeight().getValue())) { // TODO 4: replace constant
					nozzle.moveToSafeZ();
					emptyBoxAlarmCounter++;
					
					if(emptyBoxAlarmCounter >=5) {
						throw new Exception("Box is empty!");
					}
				}
				
				
				Thread.sleep(dwellOnZStep); 
				p1 = pressure(nozzle);
				Logger.trace(getName() + ": pressure = {}, delta = {}, dZ = {}", p1, p1-p0, dZ);
				
				if(stirDir == 4) {
					dZ += zStepOnPickup.getValue();
				}
				
				if(isZSwitchActivated(nozzle)) {
					zSwitchWasActivated = true;
					zSwitchActivationCounter++;
					Logger.warn(getName() + ": Z-Switch was activated (counter = " + 
							Integer.toString(zSwitchActivationCounter) + " )");
				}
	    	}
			
   	
	        // * move up to save z. 
			// on low pressure, we go very slow
			if(p1-p0 < 0.25*pressureDelta && p1 - p0 >= pressureDelta) { // TODO 4: replace constant
				directMoveTo(nozzle, location, 0.01); // slow move up
			}else {
				directMoveTo(nozzle, location); // normal fast move up
			}
			Thread.sleep(dwellOnZStep); 
			p1 = pressure(nozzle);
			
			retries--;
			
        	if(zSwitchWasActivated) {
        		dZ += 3;
        	}
            setLastCatchZDepth(lastCatchZDepth.add(dZ));
    	}while(p1-p0 < pressureDelta && retries > 0);
    	
    	
        // * move up to save z. 
		// on low pressure, we go very slow
		if(p1-p0 < 0.500) { // TODO 4: replace constant
			nozzle.moveToSafeZ(0.1);
		}else {
			nozzle.moveToSafeZ();
		}
		Thread.sleep(dwellOnZStep); 
		p1 = pressure(nozzle);
    	
		if(retries > 0 && p1-p0 >= pressureDelta) {
            Logger.trace(getName() + ": Part(s) catched!");
		}else {
			Logger.trace(getName() + ": Could not catch a part from the heap");
			
			throw new Exception("Feeder " + getName() + ": Could not catch a part from the heap. Pressure limit not reached.");
		}
    }
    
    private void pumpOn() throws Exception {
    	getDriver().actuate(pump() , true); 
    }
    
    private void pumpOff() throws Exception {
    	getDriver().actuate(pump() , false); 
    }
    
    private void flip() throws Exception {
    	getDriver().actuate(chipFlipper() , true); 
    }
    
    private void valveOn(Nozzle nozzle) throws Exception {
    	getDriver().actuate(valve(nozzle) , true); 
    }
    
    private void valveOff(Nozzle nozzle) throws Exception {
    	getDriver().actuate(valve(nozzle) , false); 
    }
    
    private double pressure(Nozzle nozzle) throws Exception {
    	String str = getDriver().actuatorRead(pressureSensor(nozzle));
    	return Double.parseDouble( str );
    }
    
    private boolean isZSwitchActivated(Nozzle nozzle) throws Exception {
    	String str = getDriver().actuatorRead(zSwitchInput(nozzle));
    	if(str.compareTo("0") == 0) {
    		return false;
    	}else {
    		return true;
    	}
    }
    
    private ReferenceActuator pump() {
    	return getActuator(pumpName);
    }
    
    private ReferenceActuator chipFlipper() {
    	return getActuator("ChipFlipper"); // TODO 4: constant
    }
    
    private ReferenceActuator valve(Nozzle nozzle) {
    	return (ReferenceActuator) nozzle.getHead().getActuatorByName(valveName);
    }
    
    private ReferenceActuator pressureSensor(Nozzle nozzle) {
    	return (ReferenceActuator) nozzle.getHead().getActuatorByName("Drucksensor"); // TODO 4: parameter
    	// return (ReferenceActuator) nozzle.getHead().getActuatorByName(((ReferenceNozzle)nozzle).getVacuumSenseActuatorName()); // TODO 4: use this Attribute
    }
    
    private ReferenceActuator zSwitchInput(Nozzle nozzle) {
    	return (ReferenceActuator) nozzle.getHead().getActuatorByName("Ueberlastschalter"); // TODO 4: parameter
    }
    
    private ReferenceActuator getActuator(String id) {
    	return (ReferenceActuator) Configuration.get().getMachine().getActuatorByName(id);
    }
    
    private ReferenceDriver getDriver() {
        return getMachine().getDriver();
    }

    private ReferenceMachine getMachine() {
        return (ReferenceMachine) Configuration.get().getMachine();
    }

    
    
    
    public String getSubBoxName() {
		return subBoxName;
	}
	public void setSubBoxName(String subBoxName) {
		this.subBoxName = subBoxName;
	}
	
	public void setDefaultLocation(int boxTrayId, String subBoxName) {
		this.boxTrayId = boxTrayId;
		this.subBoxName = subBoxName;
		
		// TODO 4: add button for update globalBoxTrayLocation.

		// TODO 2: take rotaion of globalBoxTrayLocation into account!
		double dX = boxTrayWallThickness.getValue() + 
				boxTrayInnerSizeX.getValue()/2 + 
				boxColumn()*(boxTrayInnerSizeX.getValue() + boxTrayWallThickness.getValue());
		double dY = boxTrayWallThickness.getValue() + 
				boxTrayInnerSizeY.getValue()/2 + 
				boxRow()*(boxTrayInnerSizeY.getValue() + boxTrayWallThickness.getValue());

		location = globalBoxTrayLocations.get(boxTrayId).add(new Location(LengthUnit.Millimeters, dX,dY,0,0));
		pickLocation = null; // reset picklocation
		
		Logger.info(getName() + ": Set Default Location to " + location.toString());
	}
	
	private int boxRow() {
		char rowChar = subBoxName.charAt(1);
		return rowChar - '1'; // starting from zero
	}
	private int boxColumn() {
		char columnChar = subBoxName.charAt(0);
		return columnChar - 'A'; // starting from zero		
	}


	public int getBoxTrayId() {
		return boxTrayId;
	}

	public void setBoxTrayId(int boxTrayId) {
		this.boxTrayId = boxTrayId;
	}


    public String getPumpName() {
		return pumpName;
	}

	public void setPumpName(String pumpName) {
		this.pumpName = pumpName;
	}

	public String getValveName() {
		return valveName;
	}

	public void setValveName(String valveName) {
		this.valveName = valveName;
	}

	public String getPressureSensorName() {
		return pressureSensorName;
	}

	public void setPressureSensorName(String pressureSensorName) {
		this.pressureSensorName = pressureSensorName;
	}

	public double getPressureDelta() {
		return pressureDelta;
	}

	public void setPressureDelta(double pressureDelta) {
		this.pressureDelta = pressureDelta;
	}

	public Length getMaxZTravel() {
		return maxZTravel;
	}

	public void setMaxZTravel(Length maxZTravel) {
		this.maxZTravel = maxZTravel;
	}

	public Length getLastCatchZDepth() {
		return lastCatchZDepth;
	}

	// TODO 3: live update z-value
	public void setLastCatchZDepth(Length lastCatchZDepth) {
		Length oldValue = lastCatchZDepth;
		this.lastCatchZDepth = lastCatchZDepth;
		firePropertyChange("lastCatchZDepth", oldValue, lastCatchZDepth);
	}

	public Length getzStepOnPickup() {
		return zStepOnPickup;
	}

	public void setzStepOnPickup(Length zStepOnPickup) {
		this.zStepOnPickup = zStepOnPickup;
	}

	public int getDwellOnZStep() {
		return dwellOnZStep;
	}

	public void setDwellOnZStep(int dwellOnZStep) {
		this.dwellOnZStep = dwellOnZStep;
	}

	private Location getNextUpsideUpLocationInDropbox(Camera camera, Nozzle nozzle) throws Exception {
		Logger.info(getName() + ": run vision for UpsideUp (normal)");
		
		currentAnythingElseCountInDropBox = -1; // who knows?
		currentUpsideDownPartsInDropBox = -1; // just in case we have an exception
		currentUpsideUpPartsInDropBox = -1;
		
		nozzle.moveToSafeZ();
		camera.moveTo(dropBoxTopLocation());
		
		CvPipeline pipeline = null;
		if(useUpsideUpPipelineFrom.isEmpty()) {
			pipeline = upsideUpPipeline;
			Logger.info(getName() + ": use own pipeline for upside up");
		}else {
			HeapFeeder otherFeeder = (HeapFeeder) getMachine().getFeederByName(useUpsideUpPipelineFrom);
			if(otherFeeder == null) {
				throw new Exception("Error: referred Feeder for Upside Up cannot be accessed");
			}else {
				pipeline = otherFeeder.upsideUpPipeline;
				Logger.info(getName() + ": use referred pipeline for upside up from " + useUpsideUpPipelineFrom);
			}
		}
		
		ArrayList<Location> locs = getLocationsFromCvPipeline(camera, nozzle, pipeline);
		if(locs.size() > 0) {
			currentUpsideUpPartsInDropBox = locs.size();
			Logger.info(getName() + ": " + Integer.toString(currentUpsideUpPartsInDropBox) + " parts found in UpsideUp vision");
			return locs.get(0);
		}else {
			currentUpsideUpPartsInDropBox = 0;
			return null;
		}
	}
		
	private Location getNextUpsideDownLocationInDropbox(Camera camera, Nozzle nozzle) throws Exception {
		Logger.info(getName() + ": run vision for UpsideDown (flipped)");
		currentAnythingElseCountInDropBox = -1; // who knows?
		currentUpsideDownPartsInDropBox = -1; // just in case we have an exception
		
		camera.moveToSafeZ();
		camera.moveTo(dropBoxTopLocation());
		
		CvPipeline pipeline = null;
		if(useUpsideDownPipelineFrom.isEmpty()) {
			pipeline = upsideDownPipeline;
			Logger.info(getName() + ": use own pipeline for upside down");
		}else {
			HeapFeeder otherFeeder = (HeapFeeder) getMachine().getFeederByName(useUpsideDownPipelineFrom);
			if(otherFeeder == null) {
				throw new Exception("Error: referred Feeder for Upside Up cannot be accessed");
			}else {
				pipeline = otherFeeder.upsideDownPipeline;
				Logger.info(getName() + ": use referred pipeline for upside down from " + useUpsideDownPipelineFrom);
			}
		}
		
		ArrayList<Location> locs = getLocationsFromCvPipeline(camera, nozzle, pipeline);
		if(locs.size() > 0) {
			int partsFound =  locs.size();
			
			// correct this value (see conventions for the HeapFeeeder CV pipelines). 
			// This must not be exact, but is a best guess. 
			currentUpsideDownPartsInDropBox = partsFound - currentUpsideUpPartsInDropBox;  
			Logger.info(getName() + ": " + Integer.toString(partsFound) + " parts found in UpsideDown vision. Probably " + 
					Integer.toString(currentUpsideDownPartsInDropBox) + " parts flipped in the DropBox");
			
			return locs.get(0);
		}else {
			currentUpsideDownPartsInDropBox = 0;
			return null;
		}
	}
		
	private Location getNextAnythingElseLocationInDropbox(Camera camera, Nozzle nozzle) throws Exception {
		Logger.info(getName() + ": run vision for AnythingElse");
		currentAnythingElseCountInDropBox = -1;
		
		nozzle.moveToSafeZ();
		camera.moveTo(dropBoxTopLocation());
		
		CvPipeline pipeline = null;
		if(useAnythingElsePipelineFrom.isEmpty()) {
			pipeline = anythingElsePipeline;
			Logger.info(getName() + ": use own pipeline for anything else");
		}else {
			HeapFeeder otherFeeder = (HeapFeeder) getMachine().getFeederByName(useAnythingElsePipelineFrom);
			if(otherFeeder == null) {
				throw new Exception("Error: referred Feeder for Anything Else cannot be accessed");
			}else {
				pipeline = otherFeeder.anythingElsePipeline;
				Logger.info(getName() + ": use referred pipeline for anything else from " + useAnythingElsePipelineFrom);
			}
		}
		
		ArrayList<Location> locs = getLocationsFromCvPipeline(camera, nozzle, pipeline);
		if(locs.size() > 0) {
			currentAnythingElseCountInDropBox = locs.size();
			return locs.get(0);
		}else {
			currentAnythingElseCountInDropBox = 0;
			return null;
		}
	}
		
	private boolean isPartInSideViewUpsideDown(Camera camera, Nozzle nozzle) throws Exception {
		
		nozzle.moveToSafeZ();
		camera.moveTo(dropBoxTopLocation());
		
		CvPipeline pipeline = null;
		if(useUpsideUpPipelineFrom.isEmpty()) {
			pipeline = anythingElsePipeline;
		}else {
			HeapFeeder otherFeeder = (HeapFeeder) getMachine().getFeederByName(useAnythingElsePipelineFrom);
			if(otherFeeder == null) {
				throw new Exception("Error: referred Feeder for Anything Else cannot be accessed");
			}else {
				pipeline = otherFeeder.anythingElsePipeline;
			}
		}
		
		// Process the pipeline to extract RotatedRect results
        pipeline.setProperty("camera", camera);
        pipeline.setProperty("nozzle", nozzle);
        pipeline.setProperty("feeder", this);
        pipeline.process();
        
        // Grab the results
        List<RotatedRect> results = (List<RotatedRect>) pipeline.getResult("results").model;
        
        pipeline.release();
   
        if (results.isEmpty()) {
            throw new Exception("Feeder " + getName() + ": No pins in side view found, missing part?");
        }

        int yLimit_px = 240; // one pixel dead zone
        
        // Count results above and below yLimit
        int countAbove = 0;
        int countBelow = 0;
        for(RotatedRect rect: results) {
        	if(rect.center.y > yLimit_px) {
        		countAbove++;
        	}
        	if(rect.center.y < yLimit_px) {
        		countBelow++;
        	}
        }
        
        if (countAbove == 0 && countBelow == 0) {
            throw new Exception("Feeder " + getName() + ": No pins in upper or lower zone found");
        }
        
        if (countAbove > 0 && countBelow > 0) {
        	throw new Exception("Feeder " + getName() + ": Found pins in upper and lower zone - indifferent result");
        }

        if (countAbove > 0) {
        	return true;
        }
        
        if (countBelow > 0) {
        	return false;
        }
        
        throw new Exception("Feeder " + getName() + ": Unhandled case in side view analysis.");
	}
		
	// only x and y values are valid, z is ignored
	private ArrayList<Location> getLocationsFromCvPipeline(Camera camera, Nozzle nozzle, CvPipeline pipeline) throws Exception{
		
		// Process the pipeline to extract RotatedRect results
        pipeline.setProperty("camera", camera);
        pipeline.setProperty("nozzle", nozzle);
        pipeline.setProperty("feeder", this);
        
        // NOTE: we did a Arduino workaround for tinyG workaround, see this posting here
        // https://groups.google.com/forum/#!topic/openpnp/7IF0e8nfNdQ

        pipeline.process();
        
        // Grab the results
        @SuppressWarnings("unchecked")
        List<RotatedRect> results = (List<RotatedRect>) pipeline.getResult(VisionUtils.PIPELINE_RESULTS_NAME).model;
        
        
        ArrayList<Location> ret = new ArrayList<Location>();
        if(results == null) {
        	Logger.warn(getName() + ": Pipeline returned Null - Please Check the pipeline!");
        	//throw new Exception(getName() + ": Pipeline returned Null - Please Check the pipeline!");
        	// TODO 2: we must ensure, that we really see the dropbox, but there are no parts any more!
        	// if the pipeline fails for any other reason, it could start to mix the things up. 
        }else {
	        for(RotatedRect result : results) {
	        	Location cvLocation = VisionUtils.getPixelLocation(camera, result.center.x, result.center.y);
	        	
	        	double angleCorrection=0;
	        	if(result.size.width < result.size.height) {
	        		angleCorrection = 90;
	        	}
	        	
	        	
	            // Get the result's Location
	            // Update the location with the result's rotation
	            cvLocation = cvLocation.derive(null, null, null, -(result.angle+angleCorrection + location.getRotation()));
	            // TODO 3: fix 180° Rotation for better speed
	        	ret.add(cvLocation);
	        }
        }

        MainFrame.get().getCameraViews().getCameraView(camera)
                .showFilteredImage(OpenCvUtils.toBufferedImage(pipeline.getWorkingImage()), 250);
        pipeline.release();
        return ret;
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

	
    public CvPipeline getUpsideUpPipeline() {
		return upsideUpPipeline;
	}


	public void setUpsideUpPipeline(CvPipeline upsideUpPipeline) {
		this.upsideUpPipeline = upsideUpPipeline;
	}


	public String getUseUpsideUpPipelineFrom() {
		return useUpsideUpPipelineFrom;
	}


	public void setUseUpsideUpPipelineFrom(String useUpsideUppipelineFrom) {
		this.useUpsideUpPipelineFrom = useUpsideUppipelineFrom;
	}


	public CvPipeline getUpsideDownPipeline() {
		return upsideDownPipeline;
	}


	public void setUpsideDownPipeline(CvPipeline upsideDownPipeline) {
		this.upsideDownPipeline = upsideDownPipeline;
	}


	public String getUseUpsideDownPipelineFrom() {
		return useUpsideDownPipelineFrom;
	}


	public void setUseUpsideDownPipelineFrom(String useUpsideDownpipelineFrom) {
		this.useUpsideDownPipelineFrom = useUpsideDownpipelineFrom;
	}

	public CvPipeline getAnythingElsePipeline() {
		return anythingElsePipeline;
	}


	public void setAnythingElesePipeline(CvPipeline anythingElesePipeline) {
		this.anythingElsePipeline = anythingElesePipeline;
	}


	public String getUseAnythingElsePipelineFrom() {
		return useAnythingElsePipelineFrom;
	}


	public void setUseAnythingElsePipelineFrom(String useAnythingElsepipelineFrom) {
		this.useAnythingElsePipelineFrom = useAnythingElsepipelineFrom;
	}


	public CvPipeline getSideViewPipeline() {
		return sideViewPipeline;
	}


	public void setSideViewPipeline(CvPipeline sideViewPipeline) {
		this.sideViewPipeline = sideViewPipeline;
	}


	public String getUseSideViewPipelineFrom() {
		return useSideViewPipelineFrom;
	}


	public void setUseSideViewPipelineFrom(String useSideViewPipelineFrom) {
		this.useSideViewPipelineFrom = useSideViewPipelineFrom;
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
    
    @Override
    public Location getLocation() {
        return location;
    }

    @Override
    // TODO 4: if this is the A1 box, this updates the globalBoxTrayLocation!
    // and updates the location of all its associated SubBoxes
    public void setLocation(Location location) {
        Object oldValue = this.location;
        this.location = location;
        firePropertyChange("location", oldValue, location);
    }
    
    
    public void moveToDropBox() {
    	try
    	{
        	Camera cam = getMachine().getDefaultHead().getDefaultCamera();
        	Nozzle nozzle = getMachine().getDefaultHead().getDefaultNozzle();
        	nozzle.moveToSafeZ();
        	cam.moveTo(dropBoxTopLocation());
    	}catch(Exception e) {
    		// TODO 4: what to do about exceptions within the wizard?
    	}
    }
    
    public void moveToSideView() {
    	try
    	{
        	Camera cam = getMachine().getDefaultHead().getDefaultCamera();
        	Nozzle nozzle = getMachine().getDefaultHead().getDefaultNozzle();
        	nozzle.moveToSafeZ();
        	cam.moveTo(sideViewLocation());
    	}catch(Exception e) {
    		// TODO 4: what to do about exceptions within the wizard?
    	}
    }
    
    // skips the backlash compensation
    // TODO 4: we assume millimeters here
    // TODO 4: only GcodeDriver is supported
    private void directMoveTo(HeadMountable generalHm, Location location) throws Exception{
    	directMoveTo(generalHm, location, getMachine().getSpeed());
    }
    
    private void directMoveToAtSafeZ(HeadMountable generalHm, Location location) throws Exception{
    	generalHm.moveToSafeZ();
    	double safeZ = generalHm.getLocation().getZ();
    	directMoveTo(generalHm, location.derive(null,  null,  safeZ, null));
    	directMoveTo(generalHm, location);
    }
    
    private void directMoveTo(HeadMountable generalHm, Location location, double speed) throws Exception{
    	ReferenceHeadMountable hm = (ReferenceHeadMountable) generalHm;
    	
    	ReferenceDriver driver =  getMachine().getDriver();
    	Location lHead = location.subtract(hm.getHeadOffsets()).convertToUnits(LengthUnit.Millimeters);

    	if(driver instanceof GcodeDriver) {
    		GcodeDriver d = (GcodeDriver) driver;

    		// TODO 4: fix this hardcoded axis handling
    		if(hm instanceof ReferenceCamera) {
        		d.moveTo(hm, lHead.derive(null,  null,  0.0,  null), speed, true); // TODO 3: fix save-z constant
    		}else {
    			d.moveTo(hm, lHead, speed, true);
    		}
    		
    	} else {
    		hm.moveTo(location);
    	}
    }
    
    // wrapper function, using backlash compensation, if configured
    private void preciseMoveTo(ReferenceHeadMountable hm, Location location) throws Exception{
    	hm.moveTo(location);
    }

}
