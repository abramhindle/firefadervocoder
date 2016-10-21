//s.boot;
//s.freeAll;
~nfreqs=16;
s.waitForBoot {
    var fftsize = 4096;
    var buf = { Buffer.alloc(s, fftsize) }.dup;
    var hop = 1/2;
    var nfreqs = ~nfreqs;
    ~freqs1 = Buffer.alloc(s,nfreqs);
    ~freqs2 = Buffer.alloc(s,nfreqs);

    PyOnce("
        pv = PhaseVocoder(hop)
        def smushit(freqs,fb,b):
            a = freqs
            ass = fb            
            # repeat frequency measure for each input freq
            bs = np.transpose(np.broadcast_to(b,(a.shape[0],b.shape[0])))
            # we want to smush the frequencies to the closest freq
            v = (ass - bs)*closeness
            vs = np.abs(v)
            newb = b + v[np.arange(v.shape[0]),np.argmin(vs,axis=1)]
            return newb        
        def fn(x,freqs):
            x = pv.forward(x)
            idx = indices(x.shape)[1]
            # repeat freqs so we can do matrix manipulations
            fb = np.broadcast_to(freqs,(fftsize/2,freqs.shape[0]))
            x = pv.shift(x, lambda y: smushit(freqs,fb,y[0]))
            x = pv.backward(x)
            return x
    ", (hop:hop,fftsize:fftsize));
    //s.freeAll;
	SynthDef(\smush,{
	    arg ain=0,out=0,amp=0.5,closeness=0.5,freqs;
        var in = SoundIn.ar([ain]);
        //var x = FFT(buf.collect(_.bufnum), in, hop);
		var x = FFT([buf[1].bufnum],in,hop);//buf.collect(_.bufnum), in, hop);
        Py("
            out(x, fn(array(x), array(freqs)))
        ", (x:x, closeness:closeness, freqs:freqs));
        Out.ar(out, amp*(0.5+(0.5*closeness))*IFFT(x));
    }).load(s);
	s.sync;
	~closeness1 = Bus.control(s,1);
	~closeness1.set(0.99);
	~closeness2 = Bus.control(s,1);
	~closeness2.set(0.99);
	s.sync;
	~synth1 = Synth(\smush,[\ain,0,\out,0,\amp,0.0,\closeness,~closeness1,\freqs,~freqs1]);
	s.sync;
	~synth2 = Synth(\smush,[\ain,1,\out,1,\amp,0.0,\closeness,~closeness2,\freqs,~freqs2]);
	s.sync;
	~synth1.set(\amp,0.01);
	~synth2.set(\amp,0.5);
	s.sync;
	~synth1.map(\closeness,~closeness1);
	~synth2.map(\closeness,~closeness2);
	s.sync;
	//~mx={Out.kr(~closeness1,(MouseY.kr + MouseX.kr)/2.0)}.play;
	//~my={Out.kr(~closeness2,MouseX.kr)}.play;
	s.sync;
	[~freqs1,~freqs2].do {|freqs| var v = (20.0+144.0).rand;
		~nfreqs.do { |i|
			freqs.set(i,i*~v);
		};
	};
	s.sync;
	Routine({
		loop {
			~closeness1.get({|x| [1,x].postln});		
			~closeness2.get({|x| [2,x].postln});
			1.0.wait;
		};
	}).play;
	//~synth1.set(\amp,0.01);
	//~synth2.set(\amp,0.01);
	~myfreqs = 4.collect{ Buffer.alloc(s,~nfreqs); };
	s.sync;
	4.do {|j|	
		var v = 144.0.rand;
		16.do { |i|
			~myfreqs[j].set(i,i*v);
		};
	};
	~myfreqs[3].get(10,{|x| x.postln});
	//~myfreqs[3].copyData(~freqs);
	~eps = 0.00001;
	~closeenv = Env([0.95, 0, 0.95,0,0.95,0,0.95,0], [0.25-~eps, ~eps ,0.25-~eps,~eps,0.25-~eps,~eps],curve:\cub);
	~ampenv = Env([3.0, 0.4,3.0,0.4,3.0,0.4,3.0,0.4], [0.25-~eps, ~eps ,0.25-~eps,~eps,0.25-~eps,~eps],curve:\cub);
	~freqenv = Env([0.0, 0.0,1.0,1.0,2.0,2.0,3.0,3.0], [0.25-~eps,~eps,0.25-~eps,~eps,0.25-~eps,~eps]);

	
	\end.postln;
};


~mapper = Routine({
	loop {
		var f = {|x,synth,closeness,freqs,slider|
			var i,close,amp,ifreqs;
			i = x * 10.0;
			close = ~closeenv.at(i);
			amp   = ~ampenv.at(i);
			ifreqs = ~freqenv.at(i);
			[x,i,close,amp,ifreqs].postln;
			synth.set(\amp,(slider>20).asInteger * amp);
			closeness.set(close);
			~myfreqs[ifreqs].copyData(freqs);
		};
		~ffrealloca.get({|x|
			f.(x,~synth1,~closeness1,~freqs1,~sliderat);
		});
		~ffreallocb.get({|x|
			f.(x,~synth2,~closeness2,~freqs2,~sliderbt);
		});		
		0.05.wait;
	};
}).play;


//~synth2.get(\out,{|x| [\out,x].postln});
//~synth2.get(\ain,{|x| [\ain,x].postln});
//~synth2.get(\closeness,{|x| [\closeness,x].postln});
//~closeness2.get({|x| x.postln});
//~closeness1.get({|x| x.postln});
//~mx={Out.kr(~closeness1,MouseX.kr)}.play;
//~my={Out.kr(~closeness2,MouseY.kr)}.play;
//~synth.set(\closeness,0.9);

//~ffrealloca
// Dumb routine does the wrong thing, reads values and sets them
 
/*
~freqenv = [
	Env([1.0, 1.0,0.0,0.0], [0.25-~eps,~eps,0.75]),
	Env([0.0, 0.0,1.0,1.0,0.0,0.0], [0.25-~eps,~eps,0.25-~eps,~eps,0.5-~eps]),
	Env([0.0, 0.0,1.0,1.0,0.0,0.0], [0.5-~eps,~eps,0.25-~eps,~eps,0.25-~eps]),
	Env([0.0, 0.0,1.0,1.0], [0.75-~eps,~eps,0.25]),
];
*/

//(~sliderat > 20) * 10
~mapper = Routine({
	loop {
		~ffrealloca.get({|x|
			var i,close,amp,freqs;
			i = x * 10.0;
			close = ~closeenv.at(i);
			amp   = ~ampenv.at(i);
			freqs = ~freqenv.at(i);
			[x,i,close,amp,freqs].postln;
			~synth.set(\amp,(amp);
			~closeness.set(close);
			~myfreqs[freqs].copyData(~freqs);
		});
		0.01.wait;
	};
}).play;
~mapper.stop

~myfreqs[3].set(0,440);
~myfreqs[3].set(1,380);
~myfreqs[3].set(2,240);
~myfreqs[3].set(3,60);
~myfreqs[3].set(4,1640);
~myfreqs[3].set(10,121);
~myfreqs[3].set(11,923);
~myfreqs[3].set(12,6000);
~myfreqs[3].set(13,481);
~myfreqs[3].set(14,1750);
s.freqscope;

~freq3rot = Routine({
	loop {
		~myfreqs[3].set(16.rand,500.linrand);
		2.0.rand.wait;
	};
}).play;


~myfreqs[2].set(0,40);
~myfreqs[2].set(1,44);
~myfreqs[2].set(2,48);
~myfreqs[2].set(3,52);
~myfreqs[2].set(4,56);
~myfreqs[2].set(5,80);
~myfreqs[2].set(6,84);
~myfreqs[2].set(7,88);
~myfreqs[2].set(8,92);
~myfreqs[2].set(9,96);
~myfreqs[2].set(6,100);
~myfreqs[2].set(15,60);
~myfreqs[2].set(11,64);
~myfreqs[2].set(12,68);
~myfreqs[2].set(13,72);
~myfreqs[2].set(14,76);



~amp = {
	|msg|
	"Setting amplitude".postln;
	msg.postln;
	~synth.set(\amp,msg[1]);	
};
OSCFunc.newMatching(~amp, '/amp');
~setteri = 0;
~setter = {
	|msg|
	msg.postln;
	msg[1..(msg.size)].do {
		|x|
		~freqs.set(~setteri % 16, x.midicps);
		~setteri = ~setteri + 1;

	};
};
OSCFunc.newMatching(~setter,'/setter');

~host = NetAddr.new("127.0.0.1", 10000);


OSCFunc.newMatching(~ender,'/end');
OSCFunc.newMatching(~intro,'/intro');
 
/*
~shost = NetAddr.new("127.0.0.1", 57120);
~shost.sendMsg("/amp", 1.0);
~shost.sendMsg("/amp", 0.0);
~shost.sendMsg("/setter",20,80,90,90,80,80,80);
~shost.sendMsg("/setter",160);
*/
(
   var v = 24.0.rand;
   ~synth.set(\amp,1.0);
   v.postln;
   16.do { |i|
    	~freqs.set(i,i*v);
   };
)

/*
// Then
//s.scope;
//s.freqscope;

// Set some freqs
~freqs.set(0,440);
~freqs.set(1,380);
~freqs.set(2,240);
~freqs.set(3,60);
~freqs.set(4,1640);

// A fun set that shows up well on the freqscope so you
// can verify what is up
~freqs.set(0,121);
~freqs.set(1,923);
~freqs.set(2,6000);
~freqs.set(3,481);
~freqs.set(4,1750);

// You get tired and let the computer do it
(
var v = 24.0.rand;
   v.postln;
   16.do { |i|
    	~freqs.set(i,i*v);
   };
)
// Then you tell the computer to keep doing it
~r = Routine({
	while({true}, {
		//16.do { |i|
		var i = 16.rand;
		~freqs.set(i,i*440.0.rand);
		//};
		1.5.rand.wait;
	})
}).play;
//~r.stop;

~r = Routine({
	while({true}, {
		//16.do { |i|
		16.do { |i|
			~freqs.set(i,i*(1024.0.rand));
		};
		1.5.rand.wait;
	})}).play;
*/
