{ pkgs ? import <nixpkgs> {} }:

let fhs = pkgs.buildFHSUserEnv {
  name = "build-fhs";
  targetPkgs = pkgs: with pkgs; [
    zlib
    gnumake
    cmake
  ];
  multiPkgs = pkgs: with pkgs; [ ncurses5 ];
  runScript = ''
    #!/usr/bin/env bash
    ./native-build "$@"

    # Clear arguments, as nix executes "$@" after this script. TODO: Refactor this ugly hack
    for i
    do
      shift;
    done
  '';
};
in pkgs.stdenv.mkDerivation {
  name = "native-build";
  nativeBuildInputs = [ fhs ];
}
