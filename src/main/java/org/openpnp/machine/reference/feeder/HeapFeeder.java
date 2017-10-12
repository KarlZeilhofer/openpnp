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
import org.openpnp.machine.reference.ReferenceDriver;
import org.openpnp.machine.reference.ReferenceFeeder;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.feeder.wizards.HeapFeederConfigurationWizard;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
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

	

 */

public class HeapFeeder extends ReferenceFeeder {
    
    
    @Attribute(required = false)
    private String pumpName = "Pumpe";
    @Attribute(required = false)
    private String valveName = "Ventil";
    @Attribute(required = false)
    private String pressureSensorName = "Drucksensor";
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
    private String subBoxName = "A1"; // TODO 2: make adjustable
	@Attribute(required = false)
    private int boxTrayId = 1; // TODO 2: make adjustable
    
	// @ElementList(required = false) // TODO 3: fix bug with Megabytes of repeated entries in machine.xml
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
    private List<Location> heapToUpcamWayPoints; // TODO 4: use this and implement in GUI or make it very intelligent. 
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

     * TODO 5: This is detected by the up-camera. Only in this case, the parts
     * are dropped on the separationArea. 
     * 
     * 
     */
    static private Location dropBoxLocation = new Location(LengthUnit.Millimeters, 401.300, 136.200, -23.0, 0.0); // TODO 4: dropBoxLocation attribute

    
    /**
     * The dropBoxPlaneLocation is a fixed location shared by all feeders of this type. 
     * 
     * It is the center of the flat area of the DropBox. It is used for the built in 
     * AdvancedLoosePartFeeder, which is  responsible for picking the parts up again. 
     */
   static private Location dropBoxPlaneLocation = new Location(LengthUnit.Millimeters, 401.300, 136.200, -23.0, 0.0); // TODO 4: dropBoxLocation attribute

    
    
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
    
    
    
    
    // @Element(required=false) TODO 2: @Element for side view location
    static private Location sideViewLocation = new Location(LengthUnit.Millimeters, 0,0,0,0); // TODO 0: side view location 
    
    /**
     * The pickLocation is a fixed location for this feeder object. 
     * The part is placed there by the feeder when all necessary operations 
     * have been passed before. 
     */
    private Location pickLocation = null;

    
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
    	
    	Logger.trace("Constructor: this.name = " + name);   	
    }
    

	@Override
	public Location getPickLocation() throws Exception {
		return pickLocation == null ? location : pickLocation;
	}

    @Override
    public void feed(Nozzle nozzle) throws Exception {
    	
        Camera camera = nozzle.getHead().getDefaultCamera();
        
    	// TODO 0: implement complete sequence of operations!
        
        /*
         Procedure:
         
         
         if(still parts on separation area)
         	Clean up (TODO)
         
         
         Pick new Part:
         * Turn on the vacuum pump and the valve
         * Go to location at save Z
         * measure pressure (with no part on nozzle)
         * move down until pressure rises significantly
         * move up to save z. 
         * follow waypoints to up-camera
         * do a bottom vision
			BottomVision:
			 * goto UpCamera
	         if(OK)
	         	* goto picklocation
	         else if(single part but upside down)
	         	Flip Chip:
	         	do
		         	* goto chipFlipLocation
		         	* drop the chip
		         	* top vision
		         	if (nothing found)
		         		* throw error
		         	else
		         		* pick it up
		        until success
		        * goto BottomVision()
		     else if(multiple parts  on nozzle)
		     	* goto separationDropLocation
		     	* drop the parts
		     	* top vision
		     	* store number of parts
		     	* find part with top-side upward
		     	if( found )
		     		* pick it up
		     		* goto BottomVision()
		     	else if (only upside down parts found)
		     		* pick one up 
		     		* goto FlipChip()
		     	else // nothing usable found
		     		* throw error
		     else // no part on nozzle found
		     	* throw error
         */
    	
        pickNewPart(camera, nozzle);
        
        moveToDropLocation(camera, nozzle);
        
        // TODO 0: use advanced loose parts feeder
        // TOOD 0: use chip flipper
    }
    
    private void moveToDropLocation(Camera camera, Nozzle nozzle) throws Exception {
    	nozzle.moveToSafeZ();
    	double saveZ = 0;
    	// TODO 3: double saveZ = nozzle.getLocation().getZ()
    	
    	Location nozLoc = location.derive(null, null, saveZ, null); // TODO 5: we assume here a save-z of 0
    	
    	nozzle.moveTo(nozLoc);
    	
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
		nozzle.moveTo(nozLoc);
		
		// * move down into the corridor
		x = null; 
		y = null;
		z = location.getZ() -5; // TODO 5: we assume here a save-z of 0
		nozLoc = nozLoc.derive(x, y, z, null);
		nozzle.moveTo(nozLoc);
		
		
		// * move towards front (near the origin of the current BoxTray) in the y-corridor
		dX=0;
		dY= -boxRow()*(boxTrayInnerSizeY.getValue() + boxTrayWallThickness.getValue()) 
				-boxTrayInnerSizeY.getValue()/2 -boxTrayWallThickness.getValue() 
				-corridorWidth.getValue()/2;
		dZ=0;
		nozLoc = nozLoc.add(new Location(LengthUnit.Millimeters, dX,dY,dZ,0));
		nozzle.moveTo(nozLoc);
		
		// * move to the right to the exit of the x-corridor
		x = globalBoxTrayLocations.get(1).getX() + 
				(3*boxTrayWallThickness.getValue() + 2*boxTrayInnerSizeX.getValue() + corridorWidth.getValue());
		y=null; 
		z=null; // dont change
		nozLoc = nozLoc.derive(x, y, z, null);
		nozzle.moveTo(nozLoc);
		
		
		// * move to final dropbox position
		nozzle.moveToSafeZ(); // move up from corridor before moving above drop location
		nozLoc = dropBoxLocation.derive(null, null, saveZ, null); // get drop box location at saveZ
		dX=-9;
		dY=+9;
		dZ=0;
		nozLoc = nozLoc.add(new Location(LengthUnit.Millimeters, dX,dY,dZ,0));
		nozzle.moveTo(nozLoc); // move above drop location at saveZ
		
		x=null; 
		y=null;
		z = dropBoxLocation.getZ();
		nozLoc = nozLoc.derive(x, y, z, null);
		nozzle.moveTo(nozLoc); // go down to drop height. 
		

		// * drop the part(s)
		valveOff(nozzle);
		
		// dummy pick location for testing with a "real" pnp-job
		pickLocation = nozzle.getLocation(); // TODO 0: remove debug code
    }
    
    /**
     * We assume that the separation area is cleaned up!
     * @param camera
     * @param nozzle
     * @return
     * @throws Exception
     */
    private void pickNewPart(Camera camera, Nozzle nozzle) throws Exception {
        // Turn on the vacuum pump and the valve
    	pumpOn();
    	valveOn(nozzle);
       	
    	MovableUtils.moveToLocationAtSafeZ(nozzle, location); // center of our box
    	
        // * measure pressure (with no part on nozzle), until it has stabilized
    	Thread.sleep(300);
    	
    	
    	// * move down until pressure rises significantly
    	// retry up to 5 times
    	double p0,p1;
    	double dZ = 0;
    	int stirDir=0; // 0 = (r,r), 1=(-r,r), 2=(-r,-r), 3=(r,-r)
		
    	int retries = 5+1;
    	do {
        	p0 = pressure(nozzle); // TODO 4: implement stabilizations algorithm
        	//dZ = -maxZTravel.getValue(); // start some millimeters above lastCatchZDepth
        	dZ = 1;
        	
	    	// Start with motion at last catch height + 1mm (improves speed)
			double r = 0.5; // radius of stiring-motion, // TODO 3: parameter
			Location lStart = location.add(new Location(LengthUnit.Millimeters, 0,0,lastCatchZDepth.getValue()+dZ, 0.0));
			
	
			p1 = p0;
			while(p1 - p0 < pressureDelta && dZ > maxZTravel.getValue() && isZSwitchActivated(nozzle) == false) {
				Location l = location;
		    	
				// TODO 3: handle collision with Box's floor
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
				
				nozzle.moveTo(l);
				Thread.sleep(dwellOnZStep); 
				p1 = pressure(nozzle);
				Logger.trace("pressure = {}, delta = {}, dZ = {}", p1, p1-p0, dZ);
				
				if(stirDir == 4) {
					dZ += zStepOnPickup.getValue();
				}
	    	}
	    	
	        // * move up to save z. 
			// on low pressure, we go very slow
			if(p1-p0 < 0.500 && p1 - p0 >= pressureDelta) { // TODO 3: replace constant
				nozzle.moveTo(location, 0.01);
			}else {
				nozzle.moveTo(location);
			}
			Thread.sleep(dwellOnZStep); 
			p1 = pressure(nozzle);
			
			retries--;
            setLastCatchZDepth(lastCatchZDepth.add(dZ));
    	}while(p1-p0 < pressureDelta && retries > 0);
    	
    	
        // * move up to save z. 
		// on low pressure, we go very slow
		if(p1-p0 < 0.500) { // TODO 3: replace constant
			nozzle.moveToSafeZ(0.1);
		}else {
			nozzle.moveToSafeZ();
		}
		Thread.sleep(dwellOnZStep); 
		p1 = pressure(nozzle);
    	
		if(retries > 0 && p1-p0 >= pressureDelta) {
            Logger.trace("Part(s) catched!");
            
            // Testing Code:
            // throw away the parts 14mm to the left of the feeder position. 
//            nozzle.moveTo(location.add(new Location(LengthUnit.Millimeters, -14, 0, 0, 0)));
//            valveOff(nozzle);
		}else {
			Logger.trace("Could not catch a part from the heap");
			
			throw new Exception("Feeder " + getName() + ": Could not catch a part from the heap. Pressure limit not reached.");
		}
		
		pickLocation = null;
    }
    
    private void pumpOn() throws Exception {
    	getDriver().actuate(pump() , true); 
    }
    
    private void pumpOff() throws Exception {
    	getDriver().actuate(pump() , false); 
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
    
    private ReferenceActuator valve(Nozzle nozzle) {
    	return (ReferenceActuator) nozzle.getHead().getActuatorByName(valveName);
    }
    
    private ReferenceActuator pressureSensor(Nozzle nozzle) {
    	return (ReferenceActuator) nozzle.getHead().getActuatorByName("Drucksensor");
    	// return (ReferenceActuator) nozzle.getHead().getActuatorByName(((ReferenceNozzle)nozzle).getVacuumSenseActuatorName()); // TODO 4: use this Attribute
    }
    
    private ReferenceActuator zSwitchInput(Nozzle nozzle) {
    	return (ReferenceActuator) nozzle.getHead().getActuatorByName("Ueberlastschalter"); // TODO 3: parameter
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
		
		Logger.info("Set Default Location to " + location.toString());
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
		
		camera.moveToSafeZ();
		camera.moveTo(dropBoxLocation);
		
		CvPipeline pipeline = null;
		if(useUpsideUpPipelineFrom.isEmpty()) {
			pipeline = upsideUpPipeline;
		}else {
			HeapFeeder otherFeeder = (HeapFeeder) getMachine().getFeederByName(useUpsideUpPipelineFrom);
			if(otherFeeder == null) {
				throw new Exception("Error: referred Feeder for Upside Up cannot be accessed");
			}else {
				pipeline = otherFeeder.upsideUpPipeline;
			}
		}
		
		return getNextLocationFromCvPipeline(camera, nozzle, pipeline);
	}
		
	private Location getNextUpsideDownLocationInDropbox(Camera camera, Nozzle nozzle) throws Exception {
		
		camera.moveToSafeZ();
		camera.moveTo(dropBoxLocation);
		
		CvPipeline pipeline = null;
		if(useUpsideDownPipelineFrom.isEmpty()) {
			pipeline = upsideDownPipeline;
		}else {
			HeapFeeder otherFeeder = (HeapFeeder) getMachine().getFeederByName(useUpsideDownPipelineFrom);
			if(otherFeeder == null) {
				throw new Exception("Error: referred Feeder for Upside Up cannot be accessed");
			}else {
				pipeline = otherFeeder.upsideDownPipeline;
			}
		}
		
		return getNextLocationFromCvPipeline(camera, nozzle, pipeline);
	}
		
	private Location getNextAnythingElseLocationInDropbox(Camera camera, Nozzle nozzle) throws Exception {
		
		camera.moveToSafeZ();
		camera.moveTo(dropBoxLocation);
		
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
		
		return getNextLocationFromCvPipeline(camera, nozzle, pipeline);
	}
		
	private boolean isPartInSideViewUpsideDown(Camera camera, Nozzle nozzle) throws Exception {
		
		camera.moveToSafeZ();
		camera.moveTo(dropBoxLocation);
		
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
		
	private Location getNextLocationFromCvPipeline(Camera camera, Nozzle nozzle, CvPipeline pipeline) throws Exception{
		
		// Process the pipeline to extract RotatedRect results
        pipeline.setProperty("camera", camera);
        pipeline.setProperty("nozzle", nozzle);
        pipeline.setProperty("feeder", this);
        pipeline.process();
        
        // Grab the results
        List<RotatedRect> results = (List<RotatedRect>) pipeline.getResult("results").model;
        if (results.isEmpty()) {
            return null;
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
    // TODO 3: if this is the A1 box, this updates the globalBoxTrayLocation!
    public void setLocation(Location location) {
        Object oldValue = this.location;
        this.location = location;
        firePropertyChange("location", oldValue, location);
    }
    
    
    public void moveToDropBox() {
    	try
    	{
        	Camera cam = getMachine().getDefaultHead().getDefaultCamera();
        	cam.moveToSafeZ();
        	cam.moveTo(dropBoxLocation);
    	}catch(Exception e) {
    		// TODO 3: what to do about exceptions within the wizard?
    	}
    }
    
    public void moveToSideView() {
    	try
    	{
        	Camera cam = getMachine().getDefaultHead().getDefaultCamera();
        	cam.moveToSafeZ();
        	cam.moveTo(sideViewLocation);
    	}catch(Exception e) {
    		// TODO 3: what to do about exceptions within the wizard?
    	}
    }
}
