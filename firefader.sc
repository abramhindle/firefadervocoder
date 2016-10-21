// Stolen from  Edgar Berdahl (eberdahl@ccrma.stanford.edu)
// and Alexandros Kontogeorgakopoulos (akontogeorgakopoulos@cardiffmet.ac.uk)
// Firefader pd patch and arduino firmware.

~firefaderport = SerialPort(
	"/dev/ttyACM0",    // Your serial port!
	baudrate: 57600,   
	crtscts: true);

~sendforces = {
	|port,forcea,forceb,scale=1.8|
	forcea = max(-127,min(126,((forcea*scale / 1.56) * 127).round));
	forceb = max(-127,min(126,((forceb*scale / 1.56) * 127).round));
	port.put(127);
	port.put(forcea);
	port.put(forceb);
};

// from 0 to 254
~ffloca = Bus.control(s,1);
~fflocb = Bus.control(s,1);
// in meters (the controller is 9.9cm long btw)
~ffrealloca = Bus.control(s,1);
~ffreallocb = Bus.control(s,1);

SynthDef(\firefaderout,{
	|in1,in2,out1,out2,freq=30|
	var input1,input2;
	input1 = In.kr(in1,1);
	input2 = In.kr(in2,1);
	// 0.00039
	Out.kr([out1,out2],LPF.kr(0.00039*[input1,input2], freq: freq));
}).load(s);

//~ffloca.scope;
//~ffrealloca.scope;

~readFader = {
	|port|
	var b = 0,vals;
	while({b != 255},{
		b = port.read();
	});
	vals = 6.collect({|i| port.read; });
	~ffloca.set(vals[0]);
	~fflocb.set(vals[1]);
	vals;
};
// So this example fourband serves as a guide for the fire fader
~eps = 0.00001;
~fourband = Env([1, -1, 1,-1,1,-1,1,-1], [0.25-~eps, ~eps ,0.25-~eps,~eps,0.25-~eps,~eps]);
~firefaderroutine = Routine({
		loop {
			//~ffloca.get({|x| x.postln; });
			~ffreallocb.get({|y|
				~ffrealloca.get({|x|
					var force1 = ~fourband.at(x*10.0);
					var force2 = ~fourband.at(y*10.0);
					~sendforces.(~firefaderport,force1,force2,1.8);
					~readFader.(~firefaderport);
					
				});
			});
			0.01.wait;
		};
}).play;
//~firefaderroutine.stop

~firefaderoutsynth = Synth(\firefaderout,[\in1,~ffloca,\in2,~fflocb,\out1,~ffrealloca,\out2,~ffrealb]);
