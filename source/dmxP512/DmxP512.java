package dmxP512;

import processing.core.*;
import processing.serial.*;

/**
 * author Henri DAVID<br/>
 * dmxP512 : DMX processing.org library<br/>
 * class for DMX output.
 * <ul>
 * <li>project info : motscousus.com/stuff/2011-01_dmxP512/</li>
 * <li>handles DMX USB Pro Enttec and LANBOX devices.</li>
 * </ul>
 * 
 * 
 */
public class DmxP512 extends Thread {

	/**
	 * message formatted for the DMX USB Pro Enttec interface.
	 */
	private byte[] dmxProMessage=null;
	/**
	 * port name for DMX USB Pro Enttec interface.
	 */
	private String dmxProSerialPortName=null;
	/**
	 * serial port speed for DMX USB Pro Enttec interface.
	 */
	private int dmxProBaudRate=-1;
	/**
	 * Serial port to communicate with the DMX PRO USB adapter.
	 */
	private Serial dmxProSerialPort;

	/**
	 * message formatted for LANBOX device.
	 */
	private byte[] lanboxMessage=null;
	/**
	 * message formatted for LANBOX device.
	 */
	private int lanboxPort;
	/**
	 * message formatted for LANBOX device.
	 */
	private String lanboxIP=null;
	/**
	 * LanBox uses an udp protocol
	 */
	private UDP udp=null;

	
	/**
	 * Number of channels.<br/>
	 * DMX PRO interface seems to require a minimum size of 25 channels.
	 */
	private int universeSize=512;
	/**
	 * time before sending DMX the frame even though it did not change. 
	 * for now we don't use this, but this could be useful for a new interface
	 * or specific application.
	 */
	private long refreshDelay=Long.MAX_VALUE;
	/**
	 * time we wait before sending a new frame.
	 */
	private long sleepDelay=1;
	/**
	 * last time a frame was sent.
	 */
	private long lastSend=System.currentTimeMillis();
	/**
	 *  has the buffer changed since last send ? 
	 */
	private boolean needSend=true;
	/**
	 * use buffered sending strategy.
	 */
	private boolean buffered=true;
	/**
	 * daddy.
	 */
	private PApplet papplet;

	/**
	 * Uses default universe size(512) and buffered sending strategy
	 * @param papplet : daddy
	 */
	public DmxP512(PApplet papplet){
		this(papplet,512);
	}

	/**
	 * Uses default buffered sending strategy
	 * @param papplet : daddy
	 * @param universeSize : size of the dmx universe (number of channels)
	 */
	public DmxP512(PApplet papplet,int universeSize){
		this(papplet,universeSize,true);
	}
	/**
	 * Creates the dmxP512 object. the interface(s) used must still be setup using the correct method.
	 * @param papplet : daddy 
	 * @param universeSize : size of the DMX universe (number of channels)
	 * @param buffered : use a buffer before sending. if false frames are sent directly. 
	 */
	public DmxP512(PApplet papplet,int universeSize,boolean buffered){
		System.out.println("DmxP512. motscousus.com/stuff/2011-01_dmxP512/");
		this.papplet=papplet;
		this.universeSize=universeSize;
		this.buffered=buffered;
		if(this.buffered){
			this.start();
		}
	}
	
	
	/**
	 * set up the DMX output for use with DMX USB Pro Enttenc interface.
	 * @param dmxProSerialPortName : port name com1 or "/dev/stxx" on unix,
	 * "/dev/tty.usbserial-ENQ9C839" on mac, "COM4" on windows. keep in mind that case in important (COM4  and not com4).
	 * if you see null pointer exceptions during sending of serial messages, mostlikely you entered the serial port name wrong.
	 */
	public void setupDmxPro(String dmxProSerialPortName){
		setupDmxPro(dmxProSerialPortName,115000);
	}
	
	/**
	 * set up the dmx output for use with DMX USB Pro Enttenc interface
	 * @param dmxProSerialPortName : port name com1 or "/dev/stxx" on unix,
	 * "/dev/tty.usbserial-ENQ9C839" on mac, "COM4" on windows. keep in mind that case in important (COM4  and not com4).
	 * if you see null pointer exceptions during sending of serial messages, mostlikely you entered the serial port name wrong.
	 * @param dmxProBaudRate : serial port speed
	 */
	public void setupDmxPro(String dmxProSerialPortName,int dmxProBaudRate){
		//list serial ports
		String[] ports = Serial.list();
		System.out.print("available serial ports : < ");
		for(String port : ports) System.out.print(port+" ");
		System.out.print(">\n");

		//setup DMXPRO message
		// Format of the dmx message:
		// message[0] = DMX_PRO_MESSAGE_START;
		// message[1] = DMX_PRO_SEND_PACKET;
		// message[2] = byte(dataSize & 255);  
		// message[3] = byte((dataSize >> 8) & 255);
		// message[4] = 0;
		// message[4 + 1] = value in channel 0
		// message[4 + 2] = value in channel 1
		// . . . 
		// message[4 + universeSize] = value in channel universeSize - 1
		// message[4 + universeSize + 1] = DMX_PRO_MESSAGE_END
		// where dataSize = universeSize + 1; 
		int dataSize = universeSize;
		dataSize++;
		byte[] message = new byte[universeSize + 6];
		message[0] = (byte)(0x7E);//DMX_PRO_MESSAGE_START
		message[1] = (byte)(6);// message type : DMX_PRO_SEND_PACKET
		message[2] = (byte)(dataSize & 255); //data size coded on two bytes
		message[3] = (byte)((dataSize >> 8) & 255);//data size coded on two bytes
		message[4] = 0;
		for (int i = 0; i < universeSize; i++)
		{
			message[5 + i] = 0;
		}
		message[universeSize + 5] = (byte)(0xE7);//DMX_PRO_MESSAGE_END;

		this.dmxProMessage=message;
		this.dmxProSerialPortName=dmxProSerialPortName;
		this.dmxProBaudRate=dmxProBaudRate;

		dmxProSerialPort = new Serial(this.papplet, this.dmxProSerialPortName, this.dmxProBaudRate);
	}
	
	/**
	 * Set up the DMX output for use with Lanbox device. 
	 * @param lanboxIP :  IP of the lanbox device. i could not find how to change the lanbox ip, the on i received was set on 192.168.1.77.
	 */
	public void setupLanbox(String lanboxIP){
		setupLanbox(lanboxIP,4777);
	}
	
	/**
	 * Set up the DMX output for use with Lanbox device. 
	 * @param lanboxIP :  IP of the lanbox device. i could not find how to change the lanbox ip, the on i received was set on 192.168.1.77.
	 * @param lanboxPort : udp port number, in case it is not matching the default port number. 
	 */
	public void setupLanbox(String lanboxIP,int lanboxPort){

		//setup LANBOX message
		// See http://vvvv.org/sites/default/files/user-files/LCplus%20Reference%20v2.0a1.pdf (p83)
		// and discussion on http://vvvv.org/forum/artnet-to-lanbox
		
		int datalen = universeSize;
		int firstChannel = 1;
		int bufferlen = 4/*header*/ + 6/*lanbox header*/ + datalen/*data*/ + 1/*trailer*/;
		int lngMess = 6/*lanbox header*/ + datalen;

		byte[] message = new byte[bufferlen];
		// header
		/*0*/    message[0] = (byte)0xC0;
		/*1*/    message[1] = (byte)0xB7;
		/*2*/    message[2] = (byte)0x0;//sequence number lq. ignored by lanbox
		/*3*/    message[3] = (byte)0x0;//sequence number hq. ingored by lanbox
		// message type
		/*4*/    message[4] = (byte)0xCA; // type Buffer write
		/*5*/    message[5] = (byte)254; // This message is almost the same, 
		//     except in opposite direction: it is sent to a LanBox to directly write into one of its buffers.
		//     The buffer id must be either 254 to write into the mixer buffer,
		//     or the id of a layer to write into, in range 1 (layer A) through 63 (layer BK).
		//message length
		/*6*/    message[6] = (byte)(HighByte(lngMess)); // HighByte length HEXA
		/*7*/    message[7] = (byte)(LowByte(lngMess)); // LowByte lenght HEXA

		//offset of the first channel
		/*8*/    message[8] = (byte)(HighByte(firstChannel)); //HighByte first channel
		/*9*/    message[9] = (byte)(LowByte(firstChannel)); // LowByte first channel

		//write channels
		//for(int i=0 ; i<datalen;i++){
		//	buffer[i+10]=(byte)data[i];
		//}

		if((10+datalen)%2!=0){
			message[10+datalen]= (byte)0xFF;
		}else{
			message=PApplet.shorten(message);
		}
		
		
		this.lanboxIP=lanboxIP;
		this.lanboxPort=lanboxPort;
		this.udp = new UDP(this.papplet);
		this.lanboxMessage=message;
		
	}

	private int HighByte(int a){
		return (a >> 8);
	}
	private int LowByte(int a){
		return (a & 0xFF);
	}

	/** 
	 * reset all channels to zero 
	 */
	public void reset(){
		for (int i = 1; i <= universeSize; i++)
		{
			if(dmxProMessage!=null){
				dmxProMessage[4 + i] = 0;
			}
			if(lanboxMessage!=null){
				lanboxMessage[9 + i] = 0;
			}
		}
	}


	/**
	 * Write value to the channel, if the value is different from the last written.
	 * @param channel
	 * @param value
	 */
	public void set(int channel, int value){
		set(channel,new int[]{value});
	}

	/**
	 * Write values to channels, starting the with the channel specified and incrementing it for each value of values.
	 * If the value is different from the last written.
	 * @param channel :  first channel
	 * @param values
	 */
	public void set(int channel, int[] values){

		boolean needSend = false;

		for(int i=0;i<values.length;i++){
			if(dmxProMessage!=null){
				if(channel + 4 + i<dmxProMessage.length){
					if (dmxProMessage[4+channel+i] != (byte)values[i])
					{
						dmxProMessage[4+channel+i] = (byte)values[i];
						needSend=true;
					}
				}
			}
			if(this.lanboxMessage!=null){
				if(channel + 9 + i<lanboxMessage.length){
					if (lanboxMessage[9+channel+i] != (byte)values[i])
					{
						lanboxMessage[9+channel+i] = (byte)values[i];
						needSend=true;
					}
				}
			}
		}

		if(needSend){
			//the message changed. let's request sending.
			if(this.buffered){
				this.needSend=true;
			}else{
				this.sendDMXFrame();
			}
		}

	}

	/** 
	 * sends dmxData to the DMX output module. 
	 */
	private void sendDMXFrame(){

		long now = System.currentTimeMillis();

		if(now-lastSend>refreshDelay || needSend||!buffered){

			//register last time of sending.
			needSend=false;
			lastSend=now;

			if(lanboxMessage!=null){
				udp.send(lanboxMessage,lanboxIP, lanboxPort);
			}

			if(dmxProMessage!=null){
				if(dmxProSerialPort!=null){
					dmxProSerialPort.write(dmxProMessage);
				}else{
					System.out.println("not sending DMX frame. serial port is null!!");
				}
			}

		}
	}

	/**
	 * background thread taking care of sending the DMX frames.
	 */
	public void run(){
		this.setName("dmxP512 thread");
			while(true){
			this.sendDMXFrame();
			try{
				sleep(sleepDelay);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

}






