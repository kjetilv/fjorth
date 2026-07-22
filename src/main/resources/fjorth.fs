\ fjorth bootstrap: words defined in Forth itself

: 2DUP ( a b -- a b a b ) OVER OVER ;
: 2DROP ( a b -- ) DROP DROP ;
: NIP ( a b -- b ) SWAP DROP ;
: TUCK ( a b -- b a b ) SWAP OVER ;
: NEGATE ( n -- -n ) 0 SWAP - ;
: ABS ( n -- |n| ) DUP 0 < IF NEGATE THEN ;
: MIN ( a b -- min ) 2DUP > IF SWAP THEN DROP ;
: MAX ( a b -- max ) 2DUP < IF SWAP THEN DROP ;
: 1+ ( n -- n+1 ) 1 + ;
: 1- ( n -- n-1 ) 1 - ;
: 0< ( n -- flag ) 0 < ;
: 0> ( n -- flag ) 0 > ;
: <> ( a b -- flag ) = 0= ;
: TRUE ( -- -1 ) -1 ;
: FALSE ( -- 0 ) 0 ;
: ?DUP ( n -- n n | 0 ) DUP IF DUP THEN ;
: CELL+ ( addr -- addr+1 ) 1 + ;
: HEX ( -- ) 16 BASE ! ;
: DECIMAL ( -- ) 10 BASE ! ;
: OCTAL ( -- 8) 8 BASE ! ;
: SPACE ( -- ) 32 EMIT ;
: SPACES ( n -- ) BEGIN DUP 0 > WHILE SPACE 1- REPEAT DROP ;
: 2@ DUP CELL+ @ SWAP @ ;
: 2R@ R> R> 2DUP >R >R SWAP ;
: 2R> R> R> SWAP ;
: 2>R SWAP >R >R ;
