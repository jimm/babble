// run a small batch of hunga.js as part of hudson

__path__.stress.hunga();

npasses = 1;

nn = new Number( new Date() );
myseed = nn/3600/48/1000;
go(1, myseed);

