//s.boot;
~nfreqs=16;
s.waitForBoot {
    var fftsize = 4096*4;
    var buf = { Buffer.alloc(s, fftsize) }.dup;
    var hop = 1/2;
    var nfreqs = ~nfreqs;
    ~freqs = Buffer.alloc(s,nfreqs);
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
    ~synth = {
	    arg amp=0.0,closeness=0.5;
        var in = AudioIn.ar([1]);
        var x = FFT(buf.collect(_.bufnum), in, hop);
        Py("
            out(x, fn(array(x), array(freqs)))
        ", (x:x, closeness:closeness, freqs:~freqs));
        Out.ar(0, amp*(0.5+(1.5*closeness))*IFFT(x));
    }.play(s);
};
~closeness = Bus.control(s,1);
~closeness.set(0.98);
~synth.map(\closeness,~closeness);
~synth.set(\amp,1.5);
//~synth.set(\closeness,0.9);

//~ffrealloca
// Dumb routine does the wrong thing, reads values and sets them
~myfreqs = 4.collect{ Buffer.alloc(s,~nfreqs); };
4.do {|j|	
	var v = 144.0.rand;
	16.do { |i|
    	~myfreqs[j].set(i,i*v);
	};
};
~myfreqs[3].get(10,{|x| x.postln});

~eps = 0.00001;
~closeenv = Env([1, 0, 1,0,1,0,1,0], [0.25-~eps, ~eps ,0.25-~eps,~eps,0.25-~eps,~eps],curve:\cub);
~closeenv.plot;
~ampenv = Env([3.0, 0.4,3.0,0.4,3.0,0.4,3.0,0.4], [0.25-~eps, ~eps ,0.25-~eps,~eps,0.25-~eps,~eps],curve:\cub);
~ampenv.plot;
~freqenv = [
	Env([1.0, 1.0,0.0,0.0], [0.25-~eps,~eps,0.75]),
	Env([0.0, 0.0,1.0,1.0,0.0,0.0], [0.25-~eps,~eps,0.25-~eps,~eps,0.5-~eps]),
	Env([0.0, 0.0,1.0,1.0,0.0,0.0], [0.5-~eps,~eps,0.25-~eps,~eps,0.25-~eps]),
	Env([0.0, 0.0,1.0,1.0], [0.75-~eps,~eps,0.25]),
];

~freqenv = Env([0.0, 0.0,1.0,1.0,2.0,2.0,3.0,3.0], [0.25-~eps,~eps,0.25-~eps,~eps,0.25-~eps,~eps]);



~mapper = Routine({
	loop {
		~ffrealloca.get({|x|
			var i,close,amp,freqs;
			i = x * 10.0;
			close = ~closeenv.at(i);
			amp   = ~ampenv.at(i);
			freqs = ~freqenv.at(i);
			[x,i,close,amp,freqs].postln;
			~synth.set(\amp,amp);
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
