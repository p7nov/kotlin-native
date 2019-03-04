# Code Coverage
Kotlin/Native has a code coverage support that is based on Clang's 
[Source-based Code Coverage](https://clang.llvm.org/docs/SourceBasedCodeCoverage.html).
 
**Please note**:
1. Coverage support is in it's very early days and is in active development.
    * Coverage information may be inaccurate.
    * Non-zero line counts can be wrong.

2. Most of described functionality will be incorporated into Gradle plugin.

### Usage

#### Compiling with coverage enabled

There are 2 compiler flags that allows to generate coverage information:
* `-Xcoverage`. Generate coverage for immediate sources.
* `-Xlibrary-to-cover=<path>`. Generate coverage for specified `klib`. 
Note that library also should be linked via `-library/-l` compiler option.

#### Running covered executable.

After running of the compiled executable (ex. `program.kexe`) `default.profraw` 
will be generated in the current working directory. `LLVM_PROFILE_DIR` environment variable 
controls output path for `*.profraw` file. So if you run your program like this:
```
$LLVM_PROFILE_DIR=build/program.profraw ./program.kexe
```
Then the output will be stored to the `build` dir as `program.profraw`.

#### Parsing `*.profraw` 

Generated file can be parsed with `llvm-profdata` utility. Basic usage:  
    ```
    llvm-profdata merge default.profraw -o program.profdata
    ```  
See [command guide](http://llvm.org/docs/CommandGuide/llvm-profdata.html) for more options.

#### Creating reports

The last step is to create report from the `program.profdata` file. 
It can be done with `llvm-cov` utility (refer to [command guide](http://llvm.org/docs/CommandGuide/llvm-cov.html) for detailed usage).
For example, we can see a basic report using:  
    `llvm-cov report program.kexe -instr-profile program.profdata`  
Or create a simple line-by-line coverage information in html:  
    `llvm-cov show program.kexe -instr-profile program.profdata  -format=html > report.html` 

### Sample
Usually coverage information is collected during running of the tests. 
Please refer to `samples/coverage` to see how it can be done.
