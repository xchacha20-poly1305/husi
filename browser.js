"use strict";
var husiBrowser = (() => {
  var __create = Object.create;
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __getProtoOf = Object.getPrototypeOf;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __commonJS = (cb, mod) => function __require() {
    try {
      return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
    } catch (e) {
      throw mod = 0, e;
    }
  };
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
    // If the importer is in node compatibility mode or this is not an ESM
    // file that has been converted to a CommonJS file using a Babel-
    // compatible transform (i.e. "__esModule" has not been set), then set
    // "default" to the CommonJS "module.exports" for node compatibility.
    isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
    mod
  ));
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

  // node_modules/qrcode/lib/can-promise.js
  var require_can_promise = __commonJS({
    "node_modules/qrcode/lib/can-promise.js"(exports, module) {
      module.exports = function() {
        return typeof Promise === "function" && Promise.prototype && Promise.prototype.then;
      };
    }
  });

  // node_modules/qrcode/lib/core/utils.js
  var require_utils = __commonJS({
    "node_modules/qrcode/lib/core/utils.js"(exports) {
      var toSJISFunction;
      var CODEWORDS_COUNT = [
        0,
        // Not used
        26,
        44,
        70,
        100,
        134,
        172,
        196,
        242,
        292,
        346,
        404,
        466,
        532,
        581,
        655,
        733,
        815,
        901,
        991,
        1085,
        1156,
        1258,
        1364,
        1474,
        1588,
        1706,
        1828,
        1921,
        2051,
        2185,
        2323,
        2465,
        2611,
        2761,
        2876,
        3034,
        3196,
        3362,
        3532,
        3706
      ];
      exports.getSymbolSize = function getSymbolSize(version2) {
        if (!version2) throw new Error('"version" cannot be null or undefined');
        if (version2 < 1 || version2 > 40) throw new Error('"version" should be in range from 1 to 40');
        return version2 * 4 + 17;
      };
      exports.getSymbolTotalCodewords = function getSymbolTotalCodewords(version2) {
        return CODEWORDS_COUNT[version2];
      };
      exports.getBCHDigit = function(data) {
        let digit = 0;
        while (data !== 0) {
          digit++;
          data >>>= 1;
        }
        return digit;
      };
      exports.setToSJISFunction = function setToSJISFunction(f) {
        if (typeof f !== "function") {
          throw new Error('"toSJISFunc" is not a valid function.');
        }
        toSJISFunction = f;
      };
      exports.isKanjiModeEnabled = function() {
        return typeof toSJISFunction !== "undefined";
      };
      exports.toSJIS = function toSJIS(kanji) {
        return toSJISFunction(kanji);
      };
    }
  });

  // node_modules/qrcode/lib/core/error-correction-level.js
  var require_error_correction_level = __commonJS({
    "node_modules/qrcode/lib/core/error-correction-level.js"(exports) {
      exports.L = { bit: 1 };
      exports.M = { bit: 0 };
      exports.Q = { bit: 3 };
      exports.H = { bit: 2 };
      function fromString(string2) {
        if (typeof string2 !== "string") {
          throw new Error("Param is not a string");
        }
        const lcStr = string2.toLowerCase();
        switch (lcStr) {
          case "l":
          case "low":
            return exports.L;
          case "m":
          case "medium":
            return exports.M;
          case "q":
          case "quartile":
            return exports.Q;
          case "h":
          case "high":
            return exports.H;
          default:
            throw new Error("Unknown EC Level: " + string2);
        }
      }
      exports.isValid = function isValid(level) {
        return level && typeof level.bit !== "undefined" && level.bit >= 0 && level.bit < 4;
      };
      exports.from = function from(value, defaultValue) {
        if (exports.isValid(value)) {
          return value;
        }
        try {
          return fromString(value);
        } catch (e) {
          return defaultValue;
        }
      };
    }
  });

  // node_modules/qrcode/lib/core/bit-buffer.js
  var require_bit_buffer = __commonJS({
    "node_modules/qrcode/lib/core/bit-buffer.js"(exports, module) {
      function BitBuffer() {
        this.buffer = [];
        this.length = 0;
      }
      BitBuffer.prototype = {
        get: function(index) {
          const bufIndex = Math.floor(index / 8);
          return (this.buffer[bufIndex] >>> 7 - index % 8 & 1) === 1;
        },
        put: function(num, length) {
          for (let i = 0; i < length; i++) {
            this.putBit((num >>> length - i - 1 & 1) === 1);
          }
        },
        getLengthInBits: function() {
          return this.length;
        },
        putBit: function(bit) {
          const bufIndex = Math.floor(this.length / 8);
          if (this.buffer.length <= bufIndex) {
            this.buffer.push(0);
          }
          if (bit) {
            this.buffer[bufIndex] |= 128 >>> this.length % 8;
          }
          this.length++;
        }
      };
      module.exports = BitBuffer;
    }
  });

  // node_modules/qrcode/lib/core/bit-matrix.js
  var require_bit_matrix = __commonJS({
    "node_modules/qrcode/lib/core/bit-matrix.js"(exports, module) {
      function BitMatrix(size) {
        if (!size || size < 1) {
          throw new Error("BitMatrix size must be defined and greater than 0");
        }
        this.size = size;
        this.data = new Uint8Array(size * size);
        this.reservedBit = new Uint8Array(size * size);
      }
      BitMatrix.prototype.set = function(row, col, value, reserved) {
        const index = row * this.size + col;
        this.data[index] = value;
        if (reserved) this.reservedBit[index] = true;
      };
      BitMatrix.prototype.get = function(row, col) {
        return this.data[row * this.size + col];
      };
      BitMatrix.prototype.xor = function(row, col, value) {
        this.data[row * this.size + col] ^= value;
      };
      BitMatrix.prototype.isReserved = function(row, col) {
        return this.reservedBit[row * this.size + col];
      };
      module.exports = BitMatrix;
    }
  });

  // node_modules/qrcode/lib/core/alignment-pattern.js
  var require_alignment_pattern = __commonJS({
    "node_modules/qrcode/lib/core/alignment-pattern.js"(exports) {
      var getSymbolSize = require_utils().getSymbolSize;
      exports.getRowColCoords = function getRowColCoords(version2) {
        if (version2 === 1) return [];
        const posCount = Math.floor(version2 / 7) + 2;
        const size = getSymbolSize(version2);
        const intervals = size === 145 ? 26 : Math.ceil((size - 13) / (2 * posCount - 2)) * 2;
        const positions = [size - 7];
        for (let i = 1; i < posCount - 1; i++) {
          positions[i] = positions[i - 1] - intervals;
        }
        positions.push(6);
        return positions.reverse();
      };
      exports.getPositions = function getPositions(version2) {
        const coords = [];
        const pos = exports.getRowColCoords(version2);
        const posLength = pos.length;
        for (let i = 0; i < posLength; i++) {
          for (let j = 0; j < posLength; j++) {
            if (i === 0 && j === 0 || // top-left
            i === 0 && j === posLength - 1 || // bottom-left
            i === posLength - 1 && j === 0) {
              continue;
            }
            coords.push([pos[i], pos[j]]);
          }
        }
        return coords;
      };
    }
  });

  // node_modules/qrcode/lib/core/finder-pattern.js
  var require_finder_pattern = __commonJS({
    "node_modules/qrcode/lib/core/finder-pattern.js"(exports) {
      var getSymbolSize = require_utils().getSymbolSize;
      var FINDER_PATTERN_SIZE = 7;
      exports.getPositions = function getPositions(version2) {
        const size = getSymbolSize(version2);
        return [
          // top-left
          [0, 0],
          // top-right
          [size - FINDER_PATTERN_SIZE, 0],
          // bottom-left
          [0, size - FINDER_PATTERN_SIZE]
        ];
      };
    }
  });

  // node_modules/qrcode/lib/core/mask-pattern.js
  var require_mask_pattern = __commonJS({
    "node_modules/qrcode/lib/core/mask-pattern.js"(exports) {
      exports.Patterns = {
        PATTERN000: 0,
        PATTERN001: 1,
        PATTERN010: 2,
        PATTERN011: 3,
        PATTERN100: 4,
        PATTERN101: 5,
        PATTERN110: 6,
        PATTERN111: 7
      };
      var PenaltyScores = {
        N1: 3,
        N2: 3,
        N3: 40,
        N4: 10
      };
      exports.isValid = function isValid(mask) {
        return mask != null && mask !== "" && !isNaN(mask) && mask >= 0 && mask <= 7;
      };
      exports.from = function from(value) {
        return exports.isValid(value) ? parseInt(value, 10) : void 0;
      };
      exports.getPenaltyN1 = function getPenaltyN1(data) {
        const size = data.size;
        let points = 0;
        let sameCountCol = 0;
        let sameCountRow = 0;
        let lastCol = null;
        let lastRow = null;
        for (let row = 0; row < size; row++) {
          sameCountCol = sameCountRow = 0;
          lastCol = lastRow = null;
          for (let col = 0; col < size; col++) {
            let module2 = data.get(row, col);
            if (module2 === lastCol) {
              sameCountCol++;
            } else {
              if (sameCountCol >= 5) points += PenaltyScores.N1 + (sameCountCol - 5);
              lastCol = module2;
              sameCountCol = 1;
            }
            module2 = data.get(col, row);
            if (module2 === lastRow) {
              sameCountRow++;
            } else {
              if (sameCountRow >= 5) points += PenaltyScores.N1 + (sameCountRow - 5);
              lastRow = module2;
              sameCountRow = 1;
            }
          }
          if (sameCountCol >= 5) points += PenaltyScores.N1 + (sameCountCol - 5);
          if (sameCountRow >= 5) points += PenaltyScores.N1 + (sameCountRow - 5);
        }
        return points;
      };
      exports.getPenaltyN2 = function getPenaltyN2(data) {
        const size = data.size;
        let points = 0;
        for (let row = 0; row < size - 1; row++) {
          for (let col = 0; col < size - 1; col++) {
            const last = data.get(row, col) + data.get(row, col + 1) + data.get(row + 1, col) + data.get(row + 1, col + 1);
            if (last === 4 || last === 0) points++;
          }
        }
        return points * PenaltyScores.N2;
      };
      exports.getPenaltyN3 = function getPenaltyN3(data) {
        const size = data.size;
        let points = 0;
        let bitsCol = 0;
        let bitsRow = 0;
        for (let row = 0; row < size; row++) {
          bitsCol = bitsRow = 0;
          for (let col = 0; col < size; col++) {
            bitsCol = bitsCol << 1 & 2047 | data.get(row, col);
            if (col >= 10 && (bitsCol === 1488 || bitsCol === 93)) points++;
            bitsRow = bitsRow << 1 & 2047 | data.get(col, row);
            if (col >= 10 && (bitsRow === 1488 || bitsRow === 93)) points++;
          }
        }
        return points * PenaltyScores.N3;
      };
      exports.getPenaltyN4 = function getPenaltyN4(data) {
        let darkCount = 0;
        const modulesCount = data.data.length;
        for (let i = 0; i < modulesCount; i++) darkCount += data.data[i];
        const k = Math.abs(Math.ceil(darkCount * 100 / modulesCount / 5) - 10);
        return k * PenaltyScores.N4;
      };
      function getMaskAt(maskPattern, i, j) {
        switch (maskPattern) {
          case exports.Patterns.PATTERN000:
            return (i + j) % 2 === 0;
          case exports.Patterns.PATTERN001:
            return i % 2 === 0;
          case exports.Patterns.PATTERN010:
            return j % 3 === 0;
          case exports.Patterns.PATTERN011:
            return (i + j) % 3 === 0;
          case exports.Patterns.PATTERN100:
            return (Math.floor(i / 2) + Math.floor(j / 3)) % 2 === 0;
          case exports.Patterns.PATTERN101:
            return i * j % 2 + i * j % 3 === 0;
          case exports.Patterns.PATTERN110:
            return (i * j % 2 + i * j % 3) % 2 === 0;
          case exports.Patterns.PATTERN111:
            return (i * j % 3 + (i + j) % 2) % 2 === 0;
          default:
            throw new Error("bad maskPattern:" + maskPattern);
        }
      }
      exports.applyMask = function applyMask(pattern, data) {
        const size = data.size;
        for (let col = 0; col < size; col++) {
          for (let row = 0; row < size; row++) {
            if (data.isReserved(row, col)) continue;
            data.xor(row, col, getMaskAt(pattern, row, col));
          }
        }
      };
      exports.getBestMask = function getBestMask(data, setupFormatFunc) {
        const numPatterns = Object.keys(exports.Patterns).length;
        let bestPattern = 0;
        let lowerPenalty = Infinity;
        for (let p = 0; p < numPatterns; p++) {
          setupFormatFunc(p);
          exports.applyMask(p, data);
          const penalty = exports.getPenaltyN1(data) + exports.getPenaltyN2(data) + exports.getPenaltyN3(data) + exports.getPenaltyN4(data);
          exports.applyMask(p, data);
          if (penalty < lowerPenalty) {
            lowerPenalty = penalty;
            bestPattern = p;
          }
        }
        return bestPattern;
      };
    }
  });

  // node_modules/qrcode/lib/core/error-correction-code.js
  var require_error_correction_code = __commonJS({
    "node_modules/qrcode/lib/core/error-correction-code.js"(exports) {
      var ECLevel = require_error_correction_level();
      var EC_BLOCKS_TABLE = [
        // L  M  Q  H
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        2,
        2,
        1,
        2,
        2,
        4,
        1,
        2,
        4,
        4,
        2,
        4,
        4,
        4,
        2,
        4,
        6,
        5,
        2,
        4,
        6,
        6,
        2,
        5,
        8,
        8,
        4,
        5,
        8,
        8,
        4,
        5,
        8,
        11,
        4,
        8,
        10,
        11,
        4,
        9,
        12,
        16,
        4,
        9,
        16,
        16,
        6,
        10,
        12,
        18,
        6,
        10,
        17,
        16,
        6,
        11,
        16,
        19,
        6,
        13,
        18,
        21,
        7,
        14,
        21,
        25,
        8,
        16,
        20,
        25,
        8,
        17,
        23,
        25,
        9,
        17,
        23,
        34,
        9,
        18,
        25,
        30,
        10,
        20,
        27,
        32,
        12,
        21,
        29,
        35,
        12,
        23,
        34,
        37,
        12,
        25,
        34,
        40,
        13,
        26,
        35,
        42,
        14,
        28,
        38,
        45,
        15,
        29,
        40,
        48,
        16,
        31,
        43,
        51,
        17,
        33,
        45,
        54,
        18,
        35,
        48,
        57,
        19,
        37,
        51,
        60,
        19,
        38,
        53,
        63,
        20,
        40,
        56,
        66,
        21,
        43,
        59,
        70,
        22,
        45,
        62,
        74,
        24,
        47,
        65,
        77,
        25,
        49,
        68,
        81
      ];
      var EC_CODEWORDS_TABLE = [
        // L  M  Q  H
        7,
        10,
        13,
        17,
        10,
        16,
        22,
        28,
        15,
        26,
        36,
        44,
        20,
        36,
        52,
        64,
        26,
        48,
        72,
        88,
        36,
        64,
        96,
        112,
        40,
        72,
        108,
        130,
        48,
        88,
        132,
        156,
        60,
        110,
        160,
        192,
        72,
        130,
        192,
        224,
        80,
        150,
        224,
        264,
        96,
        176,
        260,
        308,
        104,
        198,
        288,
        352,
        120,
        216,
        320,
        384,
        132,
        240,
        360,
        432,
        144,
        280,
        408,
        480,
        168,
        308,
        448,
        532,
        180,
        338,
        504,
        588,
        196,
        364,
        546,
        650,
        224,
        416,
        600,
        700,
        224,
        442,
        644,
        750,
        252,
        476,
        690,
        816,
        270,
        504,
        750,
        900,
        300,
        560,
        810,
        960,
        312,
        588,
        870,
        1050,
        336,
        644,
        952,
        1110,
        360,
        700,
        1020,
        1200,
        390,
        728,
        1050,
        1260,
        420,
        784,
        1140,
        1350,
        450,
        812,
        1200,
        1440,
        480,
        868,
        1290,
        1530,
        510,
        924,
        1350,
        1620,
        540,
        980,
        1440,
        1710,
        570,
        1036,
        1530,
        1800,
        570,
        1064,
        1590,
        1890,
        600,
        1120,
        1680,
        1980,
        630,
        1204,
        1770,
        2100,
        660,
        1260,
        1860,
        2220,
        720,
        1316,
        1950,
        2310,
        750,
        1372,
        2040,
        2430
      ];
      exports.getBlocksCount = function getBlocksCount(version2, errorCorrectionLevel) {
        switch (errorCorrectionLevel) {
          case ECLevel.L:
            return EC_BLOCKS_TABLE[(version2 - 1) * 4 + 0];
          case ECLevel.M:
            return EC_BLOCKS_TABLE[(version2 - 1) * 4 + 1];
          case ECLevel.Q:
            return EC_BLOCKS_TABLE[(version2 - 1) * 4 + 2];
          case ECLevel.H:
            return EC_BLOCKS_TABLE[(version2 - 1) * 4 + 3];
          default:
            return void 0;
        }
      };
      exports.getTotalCodewordsCount = function getTotalCodewordsCount(version2, errorCorrectionLevel) {
        switch (errorCorrectionLevel) {
          case ECLevel.L:
            return EC_CODEWORDS_TABLE[(version2 - 1) * 4 + 0];
          case ECLevel.M:
            return EC_CODEWORDS_TABLE[(version2 - 1) * 4 + 1];
          case ECLevel.Q:
            return EC_CODEWORDS_TABLE[(version2 - 1) * 4 + 2];
          case ECLevel.H:
            return EC_CODEWORDS_TABLE[(version2 - 1) * 4 + 3];
          default:
            return void 0;
        }
      };
    }
  });

  // node_modules/qrcode/lib/core/galois-field.js
  var require_galois_field = __commonJS({
    "node_modules/qrcode/lib/core/galois-field.js"(exports) {
      var EXP_TABLE = new Uint8Array(512);
      var LOG_TABLE = new Uint8Array(256);
      (function initTables() {
        let x = 1;
        for (let i = 0; i < 255; i++) {
          EXP_TABLE[i] = x;
          LOG_TABLE[x] = i;
          x <<= 1;
          if (x & 256) {
            x ^= 285;
          }
        }
        for (let i = 255; i < 512; i++) {
          EXP_TABLE[i] = EXP_TABLE[i - 255];
        }
      })();
      exports.log = function log2(n) {
        if (n < 1) throw new Error("log(" + n + ")");
        return LOG_TABLE[n];
      };
      exports.exp = function exp(n) {
        return EXP_TABLE[n];
      };
      exports.mul = function mul(x, y) {
        if (x === 0 || y === 0) return 0;
        return EXP_TABLE[LOG_TABLE[x] + LOG_TABLE[y]];
      };
    }
  });

  // node_modules/qrcode/lib/core/polynomial.js
  var require_polynomial = __commonJS({
    "node_modules/qrcode/lib/core/polynomial.js"(exports) {
      var GF = require_galois_field();
      exports.mul = function mul(p1, p2) {
        const coeff = new Uint8Array(p1.length + p2.length - 1);
        for (let i = 0; i < p1.length; i++) {
          for (let j = 0; j < p2.length; j++) {
            coeff[i + j] ^= GF.mul(p1[i], p2[j]);
          }
        }
        return coeff;
      };
      exports.mod = function mod(divident, divisor) {
        let result = new Uint8Array(divident);
        while (result.length - divisor.length >= 0) {
          const coeff = result[0];
          for (let i = 0; i < divisor.length; i++) {
            result[i] ^= GF.mul(divisor[i], coeff);
          }
          let offset = 0;
          while (offset < result.length && result[offset] === 0) offset++;
          result = result.slice(offset);
        }
        return result;
      };
      exports.generateECPolynomial = function generateECPolynomial(degree) {
        let poly = new Uint8Array([1]);
        for (let i = 0; i < degree; i++) {
          poly = exports.mul(poly, new Uint8Array([1, GF.exp(i)]));
        }
        return poly;
      };
    }
  });

  // node_modules/qrcode/lib/core/reed-solomon-encoder.js
  var require_reed_solomon_encoder = __commonJS({
    "node_modules/qrcode/lib/core/reed-solomon-encoder.js"(exports, module) {
      var Polynomial = require_polynomial();
      function ReedSolomonEncoder(degree) {
        this.genPoly = void 0;
        this.degree = degree;
        if (this.degree) this.initialize(this.degree);
      }
      ReedSolomonEncoder.prototype.initialize = function initialize(degree) {
        this.degree = degree;
        this.genPoly = Polynomial.generateECPolynomial(this.degree);
      };
      ReedSolomonEncoder.prototype.encode = function encode(data) {
        if (!this.genPoly) {
          throw new Error("Encoder not initialized");
        }
        const paddedData = new Uint8Array(data.length + this.degree);
        paddedData.set(data);
        const remainder = Polynomial.mod(paddedData, this.genPoly);
        const start = this.degree - remainder.length;
        if (start > 0) {
          const buff = new Uint8Array(this.degree);
          buff.set(remainder, start);
          return buff;
        }
        return remainder;
      };
      module.exports = ReedSolomonEncoder;
    }
  });

  // node_modules/qrcode/lib/core/version-check.js
  var require_version_check = __commonJS({
    "node_modules/qrcode/lib/core/version-check.js"(exports) {
      exports.isValid = function isValid(version2) {
        return !isNaN(version2) && version2 >= 1 && version2 <= 40;
      };
    }
  });

  // node_modules/qrcode/lib/core/regex.js
  var require_regex = __commonJS({
    "node_modules/qrcode/lib/core/regex.js"(exports) {
      var numeric = "[0-9]+";
      var alphanumeric = "[A-Z $%*+\\-./:]+";
      var kanji = "(?:[u3000-u303F]|[u3040-u309F]|[u30A0-u30FF]|[uFF00-uFFEF]|[u4E00-u9FAF]|[u2605-u2606]|[u2190-u2195]|u203B|[u2010u2015u2018u2019u2025u2026u201Cu201Du2225u2260]|[u0391-u0451]|[u00A7u00A8u00B1u00B4u00D7u00F7])+";
      kanji = kanji.replace(/u/g, "\\u");
      var byte = "(?:(?![A-Z0-9 $%*+\\-./:]|" + kanji + ")(?:.|[\r\n]))+";
      exports.KANJI = new RegExp(kanji, "g");
      exports.BYTE_KANJI = new RegExp("[^A-Z0-9 $%*+\\-./:]+", "g");
      exports.BYTE = new RegExp(byte, "g");
      exports.NUMERIC = new RegExp(numeric, "g");
      exports.ALPHANUMERIC = new RegExp(alphanumeric, "g");
      var TEST_KANJI = new RegExp("^" + kanji + "$");
      var TEST_NUMERIC = new RegExp("^" + numeric + "$");
      var TEST_ALPHANUMERIC = new RegExp("^[A-Z0-9 $%*+\\-./:]+$");
      exports.testKanji = function testKanji(str) {
        return TEST_KANJI.test(str);
      };
      exports.testNumeric = function testNumeric(str) {
        return TEST_NUMERIC.test(str);
      };
      exports.testAlphanumeric = function testAlphanumeric(str) {
        return TEST_ALPHANUMERIC.test(str);
      };
    }
  });

  // node_modules/qrcode/lib/core/mode.js
  var require_mode = __commonJS({
    "node_modules/qrcode/lib/core/mode.js"(exports) {
      var VersionCheck = require_version_check();
      var Regex = require_regex();
      exports.NUMERIC = {
        id: "Numeric",
        bit: 1 << 0,
        ccBits: [10, 12, 14]
      };
      exports.ALPHANUMERIC = {
        id: "Alphanumeric",
        bit: 1 << 1,
        ccBits: [9, 11, 13]
      };
      exports.BYTE = {
        id: "Byte",
        bit: 1 << 2,
        ccBits: [8, 16, 16]
      };
      exports.KANJI = {
        id: "Kanji",
        bit: 1 << 3,
        ccBits: [8, 10, 12]
      };
      exports.MIXED = {
        bit: -1
      };
      exports.getCharCountIndicator = function getCharCountIndicator(mode, version2) {
        if (!mode.ccBits) throw new Error("Invalid mode: " + mode);
        if (!VersionCheck.isValid(version2)) {
          throw new Error("Invalid version: " + version2);
        }
        if (version2 >= 1 && version2 < 10) return mode.ccBits[0];
        else if (version2 < 27) return mode.ccBits[1];
        return mode.ccBits[2];
      };
      exports.getBestModeForData = function getBestModeForData(dataStr) {
        if (Regex.testNumeric(dataStr)) return exports.NUMERIC;
        else if (Regex.testAlphanumeric(dataStr)) return exports.ALPHANUMERIC;
        else if (Regex.testKanji(dataStr)) return exports.KANJI;
        else return exports.BYTE;
      };
      exports.toString = function toString(mode) {
        if (mode && mode.id) return mode.id;
        throw new Error("Invalid mode");
      };
      exports.isValid = function isValid(mode) {
        return mode && mode.bit && mode.ccBits;
      };
      function fromString(string2) {
        if (typeof string2 !== "string") {
          throw new Error("Param is not a string");
        }
        const lcStr = string2.toLowerCase();
        switch (lcStr) {
          case "numeric":
            return exports.NUMERIC;
          case "alphanumeric":
            return exports.ALPHANUMERIC;
          case "kanji":
            return exports.KANJI;
          case "byte":
            return exports.BYTE;
          default:
            throw new Error("Unknown mode: " + string2);
        }
      }
      exports.from = function from(value, defaultValue) {
        if (exports.isValid(value)) {
          return value;
        }
        try {
          return fromString(value);
        } catch (e) {
          return defaultValue;
        }
      };
    }
  });

  // node_modules/qrcode/lib/core/version.js
  var require_version = __commonJS({
    "node_modules/qrcode/lib/core/version.js"(exports) {
      var Utils = require_utils();
      var ECCode = require_error_correction_code();
      var ECLevel = require_error_correction_level();
      var Mode = require_mode();
      var VersionCheck = require_version_check();
      var G18 = 1 << 12 | 1 << 11 | 1 << 10 | 1 << 9 | 1 << 8 | 1 << 5 | 1 << 2 | 1 << 0;
      var G18_BCH = Utils.getBCHDigit(G18);
      function getBestVersionForDataLength(mode, length, errorCorrectionLevel) {
        for (let currentVersion = 1; currentVersion <= 40; currentVersion++) {
          if (length <= exports.getCapacity(currentVersion, errorCorrectionLevel, mode)) {
            return currentVersion;
          }
        }
        return void 0;
      }
      function getReservedBitsCount(mode, version2) {
        return Mode.getCharCountIndicator(mode, version2) + 4;
      }
      function getTotalBitsFromDataArray(segments, version2) {
        let totalBits = 0;
        segments.forEach(function(data) {
          const reservedBits = getReservedBitsCount(data.mode, version2);
          totalBits += reservedBits + data.getBitsLength();
        });
        return totalBits;
      }
      function getBestVersionForMixedData(segments, errorCorrectionLevel) {
        for (let currentVersion = 1; currentVersion <= 40; currentVersion++) {
          const length = getTotalBitsFromDataArray(segments, currentVersion);
          if (length <= exports.getCapacity(currentVersion, errorCorrectionLevel, Mode.MIXED)) {
            return currentVersion;
          }
        }
        return void 0;
      }
      exports.from = function from(value, defaultValue) {
        if (VersionCheck.isValid(value)) {
          return parseInt(value, 10);
        }
        return defaultValue;
      };
      exports.getCapacity = function getCapacity(version2, errorCorrectionLevel, mode) {
        if (!VersionCheck.isValid(version2)) {
          throw new Error("Invalid QR Code version");
        }
        if (typeof mode === "undefined") mode = Mode.BYTE;
        const totalCodewords = Utils.getSymbolTotalCodewords(version2);
        const ecTotalCodewords = ECCode.getTotalCodewordsCount(version2, errorCorrectionLevel);
        const dataTotalCodewordsBits = (totalCodewords - ecTotalCodewords) * 8;
        if (mode === Mode.MIXED) return dataTotalCodewordsBits;
        const usableBits = dataTotalCodewordsBits - getReservedBitsCount(mode, version2);
        switch (mode) {
          case Mode.NUMERIC:
            return Math.floor(usableBits / 10 * 3);
          case Mode.ALPHANUMERIC:
            return Math.floor(usableBits / 11 * 2);
          case Mode.KANJI:
            return Math.floor(usableBits / 13);
          case Mode.BYTE:
          default:
            return Math.floor(usableBits / 8);
        }
      };
      exports.getBestVersionForData = function getBestVersionForData(data, errorCorrectionLevel) {
        let seg;
        const ecl = ECLevel.from(errorCorrectionLevel, ECLevel.M);
        if (Array.isArray(data)) {
          if (data.length > 1) {
            return getBestVersionForMixedData(data, ecl);
          }
          if (data.length === 0) {
            return 1;
          }
          seg = data[0];
        } else {
          seg = data;
        }
        return getBestVersionForDataLength(seg.mode, seg.getLength(), ecl);
      };
      exports.getEncodedBits = function getEncodedBits(version2) {
        if (!VersionCheck.isValid(version2) || version2 < 7) {
          throw new Error("Invalid QR Code version");
        }
        let d = version2 << 12;
        while (Utils.getBCHDigit(d) - G18_BCH >= 0) {
          d ^= G18 << Utils.getBCHDigit(d) - G18_BCH;
        }
        return version2 << 12 | d;
      };
    }
  });

  // node_modules/qrcode/lib/core/format-info.js
  var require_format_info = __commonJS({
    "node_modules/qrcode/lib/core/format-info.js"(exports) {
      var Utils = require_utils();
      var G15 = 1 << 10 | 1 << 8 | 1 << 5 | 1 << 4 | 1 << 2 | 1 << 1 | 1 << 0;
      var G15_MASK = 1 << 14 | 1 << 12 | 1 << 10 | 1 << 4 | 1 << 1;
      var G15_BCH = Utils.getBCHDigit(G15);
      exports.getEncodedBits = function getEncodedBits(errorCorrectionLevel, mask) {
        const data = errorCorrectionLevel.bit << 3 | mask;
        let d = data << 10;
        while (Utils.getBCHDigit(d) - G15_BCH >= 0) {
          d ^= G15 << Utils.getBCHDigit(d) - G15_BCH;
        }
        return (data << 10 | d) ^ G15_MASK;
      };
    }
  });

  // node_modules/qrcode/lib/core/numeric-data.js
  var require_numeric_data = __commonJS({
    "node_modules/qrcode/lib/core/numeric-data.js"(exports, module) {
      var Mode = require_mode();
      function NumericData(data) {
        this.mode = Mode.NUMERIC;
        this.data = data.toString();
      }
      NumericData.getBitsLength = function getBitsLength(length) {
        return 10 * Math.floor(length / 3) + (length % 3 ? length % 3 * 3 + 1 : 0);
      };
      NumericData.prototype.getLength = function getLength() {
        return this.data.length;
      };
      NumericData.prototype.getBitsLength = function getBitsLength() {
        return NumericData.getBitsLength(this.data.length);
      };
      NumericData.prototype.write = function write(bitBuffer) {
        let i, group, value;
        for (i = 0; i + 3 <= this.data.length; i += 3) {
          group = this.data.substr(i, 3);
          value = parseInt(group, 10);
          bitBuffer.put(value, 10);
        }
        const remainingNum = this.data.length - i;
        if (remainingNum > 0) {
          group = this.data.substr(i);
          value = parseInt(group, 10);
          bitBuffer.put(value, remainingNum * 3 + 1);
        }
      };
      module.exports = NumericData;
    }
  });

  // node_modules/qrcode/lib/core/alphanumeric-data.js
  var require_alphanumeric_data = __commonJS({
    "node_modules/qrcode/lib/core/alphanumeric-data.js"(exports, module) {
      var Mode = require_mode();
      var ALPHA_NUM_CHARS = [
        "0",
        "1",
        "2",
        "3",
        "4",
        "5",
        "6",
        "7",
        "8",
        "9",
        "A",
        "B",
        "C",
        "D",
        "E",
        "F",
        "G",
        "H",
        "I",
        "J",
        "K",
        "L",
        "M",
        "N",
        "O",
        "P",
        "Q",
        "R",
        "S",
        "T",
        "U",
        "V",
        "W",
        "X",
        "Y",
        "Z",
        " ",
        "$",
        "%",
        "*",
        "+",
        "-",
        ".",
        "/",
        ":"
      ];
      function AlphanumericData(data) {
        this.mode = Mode.ALPHANUMERIC;
        this.data = data;
      }
      AlphanumericData.getBitsLength = function getBitsLength(length) {
        return 11 * Math.floor(length / 2) + 6 * (length % 2);
      };
      AlphanumericData.prototype.getLength = function getLength() {
        return this.data.length;
      };
      AlphanumericData.prototype.getBitsLength = function getBitsLength() {
        return AlphanumericData.getBitsLength(this.data.length);
      };
      AlphanumericData.prototype.write = function write(bitBuffer) {
        let i;
        for (i = 0; i + 2 <= this.data.length; i += 2) {
          let value = ALPHA_NUM_CHARS.indexOf(this.data[i]) * 45;
          value += ALPHA_NUM_CHARS.indexOf(this.data[i + 1]);
          bitBuffer.put(value, 11);
        }
        if (this.data.length % 2) {
          bitBuffer.put(ALPHA_NUM_CHARS.indexOf(this.data[i]), 6);
        }
      };
      module.exports = AlphanumericData;
    }
  });

  // node_modules/qrcode/lib/core/byte-data.js
  var require_byte_data = __commonJS({
    "node_modules/qrcode/lib/core/byte-data.js"(exports, module) {
      var Mode = require_mode();
      function ByteData(data) {
        this.mode = Mode.BYTE;
        if (typeof data === "string") {
          this.data = new TextEncoder().encode(data);
        } else {
          this.data = new Uint8Array(data);
        }
      }
      ByteData.getBitsLength = function getBitsLength(length) {
        return length * 8;
      };
      ByteData.prototype.getLength = function getLength() {
        return this.data.length;
      };
      ByteData.prototype.getBitsLength = function getBitsLength() {
        return ByteData.getBitsLength(this.data.length);
      };
      ByteData.prototype.write = function(bitBuffer) {
        for (let i = 0, l = this.data.length; i < l; i++) {
          bitBuffer.put(this.data[i], 8);
        }
      };
      module.exports = ByteData;
    }
  });

  // node_modules/qrcode/lib/core/kanji-data.js
  var require_kanji_data = __commonJS({
    "node_modules/qrcode/lib/core/kanji-data.js"(exports, module) {
      var Mode = require_mode();
      var Utils = require_utils();
      function KanjiData(data) {
        this.mode = Mode.KANJI;
        this.data = data;
      }
      KanjiData.getBitsLength = function getBitsLength(length) {
        return length * 13;
      };
      KanjiData.prototype.getLength = function getLength() {
        return this.data.length;
      };
      KanjiData.prototype.getBitsLength = function getBitsLength() {
        return KanjiData.getBitsLength(this.data.length);
      };
      KanjiData.prototype.write = function(bitBuffer) {
        let i;
        for (i = 0; i < this.data.length; i++) {
          let value = Utils.toSJIS(this.data[i]);
          if (value >= 33088 && value <= 40956) {
            value -= 33088;
          } else if (value >= 57408 && value <= 60351) {
            value -= 49472;
          } else {
            throw new Error(
              "Invalid SJIS character: " + this.data[i] + "\nMake sure your charset is UTF-8"
            );
          }
          value = (value >>> 8 & 255) * 192 + (value & 255);
          bitBuffer.put(value, 13);
        }
      };
      module.exports = KanjiData;
    }
  });

  // node_modules/dijkstrajs/dijkstra.js
  var require_dijkstra = __commonJS({
    "node_modules/dijkstrajs/dijkstra.js"(exports, module) {
      "use strict";
      var dijkstra = {
        single_source_shortest_paths: function(graph, s, d) {
          var predecessors = {};
          var costs = {};
          costs[s] = 0;
          var open = dijkstra.PriorityQueue.make();
          open.push(s, 0);
          var closest, u, v, cost_of_s_to_u, adjacent_nodes, cost_of_e, cost_of_s_to_u_plus_cost_of_e, cost_of_s_to_v, first_visit;
          while (!open.empty()) {
            closest = open.pop();
            u = closest.value;
            cost_of_s_to_u = closest.cost;
            adjacent_nodes = graph[u] || {};
            for (v in adjacent_nodes) {
              if (adjacent_nodes.hasOwnProperty(v)) {
                cost_of_e = adjacent_nodes[v];
                cost_of_s_to_u_plus_cost_of_e = cost_of_s_to_u + cost_of_e;
                cost_of_s_to_v = costs[v];
                first_visit = typeof costs[v] === "undefined";
                if (first_visit || cost_of_s_to_v > cost_of_s_to_u_plus_cost_of_e) {
                  costs[v] = cost_of_s_to_u_plus_cost_of_e;
                  open.push(v, cost_of_s_to_u_plus_cost_of_e);
                  predecessors[v] = u;
                }
              }
            }
          }
          if (typeof d !== "undefined" && typeof costs[d] === "undefined") {
            var msg = ["Could not find a path from ", s, " to ", d, "."].join("");
            throw new Error(msg);
          }
          return predecessors;
        },
        extract_shortest_path_from_predecessor_list: function(predecessors, d) {
          var nodes = [];
          var u = d;
          var predecessor;
          while (u) {
            nodes.push(u);
            predecessor = predecessors[u];
            u = predecessors[u];
          }
          nodes.reverse();
          return nodes;
        },
        find_path: function(graph, s, d) {
          var predecessors = dijkstra.single_source_shortest_paths(graph, s, d);
          return dijkstra.extract_shortest_path_from_predecessor_list(
            predecessors,
            d
          );
        },
        /**
         * A very naive priority queue implementation.
         */
        PriorityQueue: {
          make: function(opts) {
            var T = dijkstra.PriorityQueue, t = {}, key;
            opts = opts || {};
            for (key in T) {
              if (T.hasOwnProperty(key)) {
                t[key] = T[key];
              }
            }
            t.queue = [];
            t.sorter = opts.sorter || T.default_sorter;
            return t;
          },
          default_sorter: function(a, b) {
            return a.cost - b.cost;
          },
          /**
           * Add a new item to the queue and ensure the highest priority element
           * is at the front of the queue.
           */
          push: function(value, cost) {
            var item = { value, cost };
            this.queue.push(item);
            this.queue.sort(this.sorter);
          },
          /**
           * Return the highest priority element in the queue.
           */
          pop: function() {
            return this.queue.shift();
          },
          empty: function() {
            return this.queue.length === 0;
          }
        }
      };
      if (typeof module !== "undefined") {
        module.exports = dijkstra;
      }
    }
  });

  // node_modules/qrcode/lib/core/segments.js
  var require_segments = __commonJS({
    "node_modules/qrcode/lib/core/segments.js"(exports) {
      var Mode = require_mode();
      var NumericData = require_numeric_data();
      var AlphanumericData = require_alphanumeric_data();
      var ByteData = require_byte_data();
      var KanjiData = require_kanji_data();
      var Regex = require_regex();
      var Utils = require_utils();
      var dijkstra = require_dijkstra();
      function getStringByteLength(str) {
        return unescape(encodeURIComponent(str)).length;
      }
      function getSegments(regex, mode, str) {
        const segments = [];
        let result;
        while ((result = regex.exec(str)) !== null) {
          segments.push({
            data: result[0],
            index: result.index,
            mode,
            length: result[0].length
          });
        }
        return segments;
      }
      function getSegmentsFromString(dataStr) {
        const numSegs = getSegments(Regex.NUMERIC, Mode.NUMERIC, dataStr);
        const alphaNumSegs = getSegments(Regex.ALPHANUMERIC, Mode.ALPHANUMERIC, dataStr);
        let byteSegs;
        let kanjiSegs;
        if (Utils.isKanjiModeEnabled()) {
          byteSegs = getSegments(Regex.BYTE, Mode.BYTE, dataStr);
          kanjiSegs = getSegments(Regex.KANJI, Mode.KANJI, dataStr);
        } else {
          byteSegs = getSegments(Regex.BYTE_KANJI, Mode.BYTE, dataStr);
          kanjiSegs = [];
        }
        const segs = numSegs.concat(alphaNumSegs, byteSegs, kanjiSegs);
        return segs.sort(function(s1, s2) {
          return s1.index - s2.index;
        }).map(function(obj) {
          return {
            data: obj.data,
            mode: obj.mode,
            length: obj.length
          };
        });
      }
      function getSegmentBitsLength(length, mode) {
        switch (mode) {
          case Mode.NUMERIC:
            return NumericData.getBitsLength(length);
          case Mode.ALPHANUMERIC:
            return AlphanumericData.getBitsLength(length);
          case Mode.KANJI:
            return KanjiData.getBitsLength(length);
          case Mode.BYTE:
            return ByteData.getBitsLength(length);
        }
      }
      function mergeSegments(segs) {
        return segs.reduce(function(acc, curr) {
          const prevSeg = acc.length - 1 >= 0 ? acc[acc.length - 1] : null;
          if (prevSeg && prevSeg.mode === curr.mode) {
            acc[acc.length - 1].data += curr.data;
            return acc;
          }
          acc.push(curr);
          return acc;
        }, []);
      }
      function buildNodes(segs) {
        const nodes = [];
        for (let i = 0; i < segs.length; i++) {
          const seg = segs[i];
          switch (seg.mode) {
            case Mode.NUMERIC:
              nodes.push([
                seg,
                { data: seg.data, mode: Mode.ALPHANUMERIC, length: seg.length },
                { data: seg.data, mode: Mode.BYTE, length: seg.length }
              ]);
              break;
            case Mode.ALPHANUMERIC:
              nodes.push([
                seg,
                { data: seg.data, mode: Mode.BYTE, length: seg.length }
              ]);
              break;
            case Mode.KANJI:
              nodes.push([
                seg,
                { data: seg.data, mode: Mode.BYTE, length: getStringByteLength(seg.data) }
              ]);
              break;
            case Mode.BYTE:
              nodes.push([
                { data: seg.data, mode: Mode.BYTE, length: getStringByteLength(seg.data) }
              ]);
          }
        }
        return nodes;
      }
      function buildGraph(nodes, version2) {
        const table = {};
        const graph = { start: {} };
        let prevNodeIds = ["start"];
        for (let i = 0; i < nodes.length; i++) {
          const nodeGroup = nodes[i];
          const currentNodeIds = [];
          for (let j = 0; j < nodeGroup.length; j++) {
            const node = nodeGroup[j];
            const key = "" + i + j;
            currentNodeIds.push(key);
            table[key] = { node, lastCount: 0 };
            graph[key] = {};
            for (let n = 0; n < prevNodeIds.length; n++) {
              const prevNodeId = prevNodeIds[n];
              if (table[prevNodeId] && table[prevNodeId].node.mode === node.mode) {
                graph[prevNodeId][key] = getSegmentBitsLength(table[prevNodeId].lastCount + node.length, node.mode) - getSegmentBitsLength(table[prevNodeId].lastCount, node.mode);
                table[prevNodeId].lastCount += node.length;
              } else {
                if (table[prevNodeId]) table[prevNodeId].lastCount = node.length;
                graph[prevNodeId][key] = getSegmentBitsLength(node.length, node.mode) + 4 + Mode.getCharCountIndicator(node.mode, version2);
              }
            }
          }
          prevNodeIds = currentNodeIds;
        }
        for (let n = 0; n < prevNodeIds.length; n++) {
          graph[prevNodeIds[n]].end = 0;
        }
        return { map: graph, table };
      }
      function buildSingleSegment(data, modesHint) {
        let mode;
        const bestMode = Mode.getBestModeForData(data);
        mode = Mode.from(modesHint, bestMode);
        if (mode !== Mode.BYTE && mode.bit < bestMode.bit) {
          throw new Error('"' + data + '" cannot be encoded with mode ' + Mode.toString(mode) + ".\n Suggested mode is: " + Mode.toString(bestMode));
        }
        if (mode === Mode.KANJI && !Utils.isKanjiModeEnabled()) {
          mode = Mode.BYTE;
        }
        switch (mode) {
          case Mode.NUMERIC:
            return new NumericData(data);
          case Mode.ALPHANUMERIC:
            return new AlphanumericData(data);
          case Mode.KANJI:
            return new KanjiData(data);
          case Mode.BYTE:
            return new ByteData(data);
        }
      }
      exports.fromArray = function fromArray(array2) {
        return array2.reduce(function(acc, seg) {
          if (typeof seg === "string") {
            acc.push(buildSingleSegment(seg, null));
          } else if (seg.data) {
            acc.push(buildSingleSegment(seg.data, seg.mode));
          }
          return acc;
        }, []);
      };
      exports.fromString = function fromString(data, version2) {
        const segs = getSegmentsFromString(data, Utils.isKanjiModeEnabled());
        const nodes = buildNodes(segs);
        const graph = buildGraph(nodes, version2);
        const path = dijkstra.find_path(graph.map, "start", "end");
        const optimizedSegs = [];
        for (let i = 1; i < path.length - 1; i++) {
          optimizedSegs.push(graph.table[path[i]].node);
        }
        return exports.fromArray(mergeSegments(optimizedSegs));
      };
      exports.rawSplit = function rawSplit(data) {
        return exports.fromArray(
          getSegmentsFromString(data, Utils.isKanjiModeEnabled())
        );
      };
    }
  });

  // node_modules/qrcode/lib/core/qrcode.js
  var require_qrcode = __commonJS({
    "node_modules/qrcode/lib/core/qrcode.js"(exports) {
      var Utils = require_utils();
      var ECLevel = require_error_correction_level();
      var BitBuffer = require_bit_buffer();
      var BitMatrix = require_bit_matrix();
      var AlignmentPattern = require_alignment_pattern();
      var FinderPattern = require_finder_pattern();
      var MaskPattern = require_mask_pattern();
      var ECCode = require_error_correction_code();
      var ReedSolomonEncoder = require_reed_solomon_encoder();
      var Version = require_version();
      var FormatInfo = require_format_info();
      var Mode = require_mode();
      var Segments = require_segments();
      function setupFinderPattern(matrix, version2) {
        const size = matrix.size;
        const pos = FinderPattern.getPositions(version2);
        for (let i = 0; i < pos.length; i++) {
          const row = pos[i][0];
          const col = pos[i][1];
          for (let r = -1; r <= 7; r++) {
            if (row + r <= -1 || size <= row + r) continue;
            for (let c = -1; c <= 7; c++) {
              if (col + c <= -1 || size <= col + c) continue;
              if (r >= 0 && r <= 6 && (c === 0 || c === 6) || c >= 0 && c <= 6 && (r === 0 || r === 6) || r >= 2 && r <= 4 && c >= 2 && c <= 4) {
                matrix.set(row + r, col + c, true, true);
              } else {
                matrix.set(row + r, col + c, false, true);
              }
            }
          }
        }
      }
      function setupTimingPattern(matrix) {
        const size = matrix.size;
        for (let r = 8; r < size - 8; r++) {
          const value = r % 2 === 0;
          matrix.set(r, 6, value, true);
          matrix.set(6, r, value, true);
        }
      }
      function setupAlignmentPattern(matrix, version2) {
        const pos = AlignmentPattern.getPositions(version2);
        for (let i = 0; i < pos.length; i++) {
          const row = pos[i][0];
          const col = pos[i][1];
          for (let r = -2; r <= 2; r++) {
            for (let c = -2; c <= 2; c++) {
              if (r === -2 || r === 2 || c === -2 || c === 2 || r === 0 && c === 0) {
                matrix.set(row + r, col + c, true, true);
              } else {
                matrix.set(row + r, col + c, false, true);
              }
            }
          }
        }
      }
      function setupVersionInfo(matrix, version2) {
        const size = matrix.size;
        const bits = Version.getEncodedBits(version2);
        let row, col, mod;
        for (let i = 0; i < 18; i++) {
          row = Math.floor(i / 3);
          col = i % 3 + size - 8 - 3;
          mod = (bits >> i & 1) === 1;
          matrix.set(row, col, mod, true);
          matrix.set(col, row, mod, true);
        }
      }
      function setupFormatInfo(matrix, errorCorrectionLevel, maskPattern) {
        const size = matrix.size;
        const bits = FormatInfo.getEncodedBits(errorCorrectionLevel, maskPattern);
        let i, mod;
        for (i = 0; i < 15; i++) {
          mod = (bits >> i & 1) === 1;
          if (i < 6) {
            matrix.set(i, 8, mod, true);
          } else if (i < 8) {
            matrix.set(i + 1, 8, mod, true);
          } else {
            matrix.set(size - 15 + i, 8, mod, true);
          }
          if (i < 8) {
            matrix.set(8, size - i - 1, mod, true);
          } else if (i < 9) {
            matrix.set(8, 15 - i - 1 + 1, mod, true);
          } else {
            matrix.set(8, 15 - i - 1, mod, true);
          }
        }
        matrix.set(size - 8, 8, 1, true);
      }
      function setupData(matrix, data) {
        const size = matrix.size;
        let inc = -1;
        let row = size - 1;
        let bitIndex = 7;
        let byteIndex = 0;
        for (let col = size - 1; col > 0; col -= 2) {
          if (col === 6) col--;
          while (true) {
            for (let c = 0; c < 2; c++) {
              if (!matrix.isReserved(row, col - c)) {
                let dark = false;
                if (byteIndex < data.length) {
                  dark = (data[byteIndex] >>> bitIndex & 1) === 1;
                }
                matrix.set(row, col - c, dark);
                bitIndex--;
                if (bitIndex === -1) {
                  byteIndex++;
                  bitIndex = 7;
                }
              }
            }
            row += inc;
            if (row < 0 || size <= row) {
              row -= inc;
              inc = -inc;
              break;
            }
          }
        }
      }
      function createData(version2, errorCorrectionLevel, segments) {
        const buffer = new BitBuffer();
        segments.forEach(function(data) {
          buffer.put(data.mode.bit, 4);
          buffer.put(data.getLength(), Mode.getCharCountIndicator(data.mode, version2));
          data.write(buffer);
        });
        const totalCodewords = Utils.getSymbolTotalCodewords(version2);
        const ecTotalCodewords = ECCode.getTotalCodewordsCount(version2, errorCorrectionLevel);
        const dataTotalCodewordsBits = (totalCodewords - ecTotalCodewords) * 8;
        if (buffer.getLengthInBits() + 4 <= dataTotalCodewordsBits) {
          buffer.put(0, 4);
        }
        while (buffer.getLengthInBits() % 8 !== 0) {
          buffer.putBit(0);
        }
        const remainingByte = (dataTotalCodewordsBits - buffer.getLengthInBits()) / 8;
        for (let i = 0; i < remainingByte; i++) {
          buffer.put(i % 2 ? 17 : 236, 8);
        }
        return createCodewords(buffer, version2, errorCorrectionLevel);
      }
      function createCodewords(bitBuffer, version2, errorCorrectionLevel) {
        const totalCodewords = Utils.getSymbolTotalCodewords(version2);
        const ecTotalCodewords = ECCode.getTotalCodewordsCount(version2, errorCorrectionLevel);
        const dataTotalCodewords = totalCodewords - ecTotalCodewords;
        const ecTotalBlocks = ECCode.getBlocksCount(version2, errorCorrectionLevel);
        const blocksInGroup2 = totalCodewords % ecTotalBlocks;
        const blocksInGroup1 = ecTotalBlocks - blocksInGroup2;
        const totalCodewordsInGroup1 = Math.floor(totalCodewords / ecTotalBlocks);
        const dataCodewordsInGroup1 = Math.floor(dataTotalCodewords / ecTotalBlocks);
        const dataCodewordsInGroup2 = dataCodewordsInGroup1 + 1;
        const ecCount = totalCodewordsInGroup1 - dataCodewordsInGroup1;
        const rs = new ReedSolomonEncoder(ecCount);
        let offset = 0;
        const dcData = new Array(ecTotalBlocks);
        const ecData = new Array(ecTotalBlocks);
        let maxDataSize = 0;
        const buffer = new Uint8Array(bitBuffer.buffer);
        for (let b = 0; b < ecTotalBlocks; b++) {
          const dataSize = b < blocksInGroup1 ? dataCodewordsInGroup1 : dataCodewordsInGroup2;
          dcData[b] = buffer.slice(offset, offset + dataSize);
          ecData[b] = rs.encode(dcData[b]);
          offset += dataSize;
          maxDataSize = Math.max(maxDataSize, dataSize);
        }
        const data = new Uint8Array(totalCodewords);
        let index = 0;
        let i, r;
        for (i = 0; i < maxDataSize; i++) {
          for (r = 0; r < ecTotalBlocks; r++) {
            if (i < dcData[r].length) {
              data[index++] = dcData[r][i];
            }
          }
        }
        for (i = 0; i < ecCount; i++) {
          for (r = 0; r < ecTotalBlocks; r++) {
            data[index++] = ecData[r][i];
          }
        }
        return data;
      }
      function createSymbol(data, version2, errorCorrectionLevel, maskPattern) {
        let segments;
        if (Array.isArray(data)) {
          segments = Segments.fromArray(data);
        } else if (typeof data === "string") {
          let estimatedVersion = version2;
          if (!estimatedVersion) {
            const rawSegments = Segments.rawSplit(data);
            estimatedVersion = Version.getBestVersionForData(rawSegments, errorCorrectionLevel);
          }
          segments = Segments.fromString(data, estimatedVersion || 40);
        } else {
          throw new Error("Invalid data");
        }
        const bestVersion = Version.getBestVersionForData(segments, errorCorrectionLevel);
        if (!bestVersion) {
          throw new Error("The amount of data is too big to be stored in a QR Code");
        }
        if (!version2) {
          version2 = bestVersion;
        } else if (version2 < bestVersion) {
          throw new Error(
            "\nThe chosen QR Code version cannot contain this amount of data.\nMinimum version required to store current data is: " + bestVersion + ".\n"
          );
        }
        const dataBits = createData(version2, errorCorrectionLevel, segments);
        const moduleCount = Utils.getSymbolSize(version2);
        const modules = new BitMatrix(moduleCount);
        setupFinderPattern(modules, version2);
        setupTimingPattern(modules);
        setupAlignmentPattern(modules, version2);
        setupFormatInfo(modules, errorCorrectionLevel, 0);
        if (version2 >= 7) {
          setupVersionInfo(modules, version2);
        }
        setupData(modules, dataBits);
        if (isNaN(maskPattern)) {
          maskPattern = MaskPattern.getBestMask(
            modules,
            setupFormatInfo.bind(null, modules, errorCorrectionLevel)
          );
        }
        MaskPattern.applyMask(maskPattern, modules);
        setupFormatInfo(modules, errorCorrectionLevel, maskPattern);
        return {
          modules,
          version: version2,
          errorCorrectionLevel,
          maskPattern,
          segments
        };
      }
      exports.create = function create(data, options) {
        if (typeof data === "undefined" || data === "") {
          throw new Error("No input text");
        }
        let errorCorrectionLevel = ECLevel.M;
        let version2;
        let mask;
        if (typeof options !== "undefined") {
          errorCorrectionLevel = ECLevel.from(options.errorCorrectionLevel, ECLevel.M);
          version2 = Version.from(options.version);
          mask = MaskPattern.from(options.maskPattern);
          if (options.toSJISFunc) {
            Utils.setToSJISFunction(options.toSJISFunc);
          }
        }
        return createSymbol(data, version2, errorCorrectionLevel, mask);
      };
    }
  });

  // node_modules/qrcode/lib/renderer/utils.js
  var require_utils2 = __commonJS({
    "node_modules/qrcode/lib/renderer/utils.js"(exports) {
      function hex2rgba(hex) {
        if (typeof hex === "number") {
          hex = hex.toString();
        }
        if (typeof hex !== "string") {
          throw new Error("Color should be defined as hex string");
        }
        let hexCode = hex.slice().replace("#", "").split("");
        if (hexCode.length < 3 || hexCode.length === 5 || hexCode.length > 8) {
          throw new Error("Invalid hex color: " + hex);
        }
        if (hexCode.length === 3 || hexCode.length === 4) {
          hexCode = Array.prototype.concat.apply([], hexCode.map(function(c) {
            return [c, c];
          }));
        }
        if (hexCode.length === 6) hexCode.push("F", "F");
        const hexValue = parseInt(hexCode.join(""), 16);
        return {
          r: hexValue >> 24 & 255,
          g: hexValue >> 16 & 255,
          b: hexValue >> 8 & 255,
          a: hexValue & 255,
          hex: "#" + hexCode.slice(0, 6).join("")
        };
      }
      exports.getOptions = function getOptions(options) {
        if (!options) options = {};
        if (!options.color) options.color = {};
        const margin = typeof options.margin === "undefined" || options.margin === null || options.margin < 0 ? 4 : options.margin;
        const width = options.width && options.width >= 21 ? options.width : void 0;
        const scale = options.scale || 4;
        return {
          width,
          scale: width ? 4 : scale,
          margin,
          color: {
            dark: hex2rgba(options.color.dark || "#000000ff"),
            light: hex2rgba(options.color.light || "#ffffffff")
          },
          type: options.type,
          rendererOpts: options.rendererOpts || {}
        };
      };
      exports.getScale = function getScale(qrSize, opts) {
        return opts.width && opts.width >= qrSize + opts.margin * 2 ? opts.width / (qrSize + opts.margin * 2) : opts.scale;
      };
      exports.getImageWidth = function getImageWidth(qrSize, opts) {
        const scale = exports.getScale(qrSize, opts);
        return Math.floor((qrSize + opts.margin * 2) * scale);
      };
      exports.qrToImageData = function qrToImageData(imgData, qr, opts) {
        const size = qr.modules.size;
        const data = qr.modules.data;
        const scale = exports.getScale(size, opts);
        const symbolSize = Math.floor((size + opts.margin * 2) * scale);
        const scaledMargin = opts.margin * scale;
        const palette = [opts.color.light, opts.color.dark];
        for (let i = 0; i < symbolSize; i++) {
          for (let j = 0; j < symbolSize; j++) {
            let posDst = (i * symbolSize + j) * 4;
            let pxColor = opts.color.light;
            if (i >= scaledMargin && j >= scaledMargin && i < symbolSize - scaledMargin && j < symbolSize - scaledMargin) {
              const iSrc = Math.floor((i - scaledMargin) / scale);
              const jSrc = Math.floor((j - scaledMargin) / scale);
              pxColor = palette[data[iSrc * size + jSrc] ? 1 : 0];
            }
            imgData[posDst++] = pxColor.r;
            imgData[posDst++] = pxColor.g;
            imgData[posDst++] = pxColor.b;
            imgData[posDst] = pxColor.a;
          }
        }
      };
    }
  });

  // node_modules/qrcode/lib/renderer/canvas.js
  var require_canvas = __commonJS({
    "node_modules/qrcode/lib/renderer/canvas.js"(exports) {
      var Utils = require_utils2();
      function clearCanvas(ctx, canvas, size) {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        if (!canvas.style) canvas.style = {};
        canvas.height = size;
        canvas.width = size;
        canvas.style.height = size + "px";
        canvas.style.width = size + "px";
      }
      function getCanvasElement() {
        try {
          return document.createElement("canvas");
        } catch (e) {
          throw new Error("You need to specify a canvas element");
        }
      }
      exports.render = function render(qrData, canvas, options) {
        let opts = options;
        let canvasEl = canvas;
        if (typeof opts === "undefined" && (!canvas || !canvas.getContext)) {
          opts = canvas;
          canvas = void 0;
        }
        if (!canvas) {
          canvasEl = getCanvasElement();
        }
        opts = Utils.getOptions(opts);
        const size = Utils.getImageWidth(qrData.modules.size, opts);
        const ctx = canvasEl.getContext("2d");
        const image = ctx.createImageData(size, size);
        Utils.qrToImageData(image.data, qrData, opts);
        clearCanvas(ctx, canvasEl, size);
        ctx.putImageData(image, 0, 0);
        return canvasEl;
      };
      exports.renderToDataURL = function renderToDataURL(qrData, canvas, options) {
        let opts = options;
        if (typeof opts === "undefined" && (!canvas || !canvas.getContext)) {
          opts = canvas;
          canvas = void 0;
        }
        if (!opts) opts = {};
        const canvasEl = exports.render(qrData, canvas, opts);
        const type = opts.type || "image/png";
        const rendererOpts = opts.rendererOpts || {};
        return canvasEl.toDataURL(type, rendererOpts.quality);
      };
    }
  });

  // node_modules/qrcode/lib/renderer/svg-tag.js
  var require_svg_tag = __commonJS({
    "node_modules/qrcode/lib/renderer/svg-tag.js"(exports) {
      var Utils = require_utils2();
      function getColorAttrib(color, attrib) {
        const alpha = color.a / 255;
        const str = attrib + '="' + color.hex + '"';
        return alpha < 1 ? str + " " + attrib + '-opacity="' + alpha.toFixed(2).slice(1) + '"' : str;
      }
      function svgCmd(cmd, x, y) {
        let str = cmd + x;
        if (typeof y !== "undefined") str += " " + y;
        return str;
      }
      function qrToPath(data, size, margin) {
        let path = "";
        let moveBy = 0;
        let newRow = false;
        let lineLength = 0;
        for (let i = 0; i < data.length; i++) {
          const col = Math.floor(i % size);
          const row = Math.floor(i / size);
          if (!col && !newRow) newRow = true;
          if (data[i]) {
            lineLength++;
            if (!(i > 0 && col > 0 && data[i - 1])) {
              path += newRow ? svgCmd("M", col + margin, 0.5 + row + margin) : svgCmd("m", moveBy, 0);
              moveBy = 0;
              newRow = false;
            }
            if (!(col + 1 < size && data[i + 1])) {
              path += svgCmd("h", lineLength);
              lineLength = 0;
            }
          } else {
            moveBy++;
          }
        }
        return path;
      }
      exports.render = function render(qrData, options, cb) {
        const opts = Utils.getOptions(options);
        const size = qrData.modules.size;
        const data = qrData.modules.data;
        const qrcodesize = size + opts.margin * 2;
        const bg = !opts.color.light.a ? "" : "<path " + getColorAttrib(opts.color.light, "fill") + ' d="M0 0h' + qrcodesize + "v" + qrcodesize + 'H0z"/>';
        const path = "<path " + getColorAttrib(opts.color.dark, "stroke") + ' d="' + qrToPath(data, size, opts.margin) + '"/>';
        const viewBox = 'viewBox="0 0 ' + qrcodesize + " " + qrcodesize + '"';
        const width = !opts.width ? "" : 'width="' + opts.width + '" height="' + opts.width + '" ';
        const svgTag = '<svg xmlns="http://www.w3.org/2000/svg" ' + width + viewBox + ' shape-rendering="crispEdges">' + bg + path + "</svg>\n";
        if (typeof cb === "function") {
          cb(null, svgTag);
        }
        return svgTag;
      };
    }
  });

  // node_modules/qrcode/lib/browser.js
  var require_browser = __commonJS({
    "node_modules/qrcode/lib/browser.js"(exports) {
      var canPromise = require_can_promise();
      var QRCode = require_qrcode();
      var CanvasRenderer = require_canvas();
      var SvgRenderer = require_svg_tag();
      function renderCanvas(renderFunc, canvas, text, opts, cb) {
        const args = [].slice.call(arguments, 1);
        const argsNum = args.length;
        const isLastArgCb = typeof args[argsNum - 1] === "function";
        if (!isLastArgCb && !canPromise()) {
          throw new Error("Callback required as last argument");
        }
        if (isLastArgCb) {
          if (argsNum < 2) {
            throw new Error("Too few arguments provided");
          }
          if (argsNum === 2) {
            cb = text;
            text = canvas;
            canvas = opts = void 0;
          } else if (argsNum === 3) {
            if (canvas.getContext && typeof cb === "undefined") {
              cb = opts;
              opts = void 0;
            } else {
              cb = opts;
              opts = text;
              text = canvas;
              canvas = void 0;
            }
          }
        } else {
          if (argsNum < 1) {
            throw new Error("Too few arguments provided");
          }
          if (argsNum === 1) {
            text = canvas;
            canvas = opts = void 0;
          } else if (argsNum === 2 && !canvas.getContext) {
            opts = text;
            text = canvas;
            canvas = void 0;
          }
          return new Promise(function(resolve, reject) {
            try {
              const data = QRCode.create(text, opts);
              resolve(renderFunc(data, canvas, opts));
            } catch (e) {
              reject(e);
            }
          });
        }
        try {
          const data = QRCode.create(text, opts);
          cb(null, renderFunc(data, canvas, opts));
        } catch (e) {
          cb(e);
        }
      }
      exports.create = QRCode.create;
      exports.toCanvas = renderCanvas.bind(null, CanvasRenderer.render);
      exports.toDataURL = renderCanvas.bind(null, CanvasRenderer.renderToDataURL);
      exports.toString = renderCanvas.bind(null, function(data, _, opts) {
        return SvgRenderer.render(data, opts);
      });
    }
  });

  // src/index.ts
  var index_exports = {};
  __export(index_exports, {
    createClashSession: () => createClashSession,
    createRemoteServerStorage: () => createRemoteServerStorage,
    qrPng: () => qrPng,
    saveFile: () => saveFile
  });

  // node_modules/tshomo/dist/errors.js
  var ClashError = class extends Error {
    kind;
    constructor(kind, message, options) {
      super(message, options);
      this.kind = kind;
      this.name = new.target.name;
    }
  };
  var InputError = class extends ClashError {
    constructor(message = "Invalid input") {
      super("input", message);
    }
  };
  var HttpError = class extends ClashError {
    method;
    resource;
    status;
    serverMessage;
    constructor(method, resource, status, serverMessage) {
      super("http", `HTTP ${status} (${method} ${resource})${serverMessage ? `: ${serverMessage}` : ""}`);
      this.method = method;
      this.resource = resource;
      this.status = status;
      this.serverMessage = serverMessage;
    }
  };
  var NetworkError = class extends ClashError {
    constructor(cause) {
      super("network", "Transport request failed", cause === void 0 ? void 0 : { cause });
    }
  };
  var TimeoutError = class extends ClashError {
    constructor() {
      super("timeout", "Operation timed out");
    }
  };
  var AbortError = class extends ClashError {
    // Only transport-sanitized reasons may be supplied here.
    constructor(cause) {
      super("abort", "Operation cancelled", cause === void 0 ? void 0 : { cause });
    }
  };
  var DecodeError = class extends ClashError {
    constructor(message = "Invalid response data") {
      super("decode", message);
    }
  };
  var OperationError = class extends ClashError {
    result;
    constructor(result) {
      super("operation", "Server reported an unsuccessful operation");
      this.result = result;
    }
  };
  var UnsupportedTransportError = class extends ClashError {
    constructor() {
      super("unsupported-transport", "Requested transport is unavailable");
    }
  };
  var UnsupportedCapabilityError = class extends ClashError {
    capability;
    constructor(capability) {
      super("unsupported-capability", `Unsupported capability: ${capability}`);
      this.capability = capability;
    }
  };
  var StreamError = class extends ClashError {
    recoverable;
    constructor(message = "Stream connection failed", recoverable = true) {
      super("stream", message);
      this.recoverable = recoverable;
    }
  };
  var BufferOverflowError = class extends ClashError {
    constructor() {
      super("buffer-overflow", "Log buffer capacity exceeded");
    }
  };
  var ClientDisposedError = class extends ClashError {
    constructor() {
      super("client-disposed", "Client has been disposed");
    }
  };

  // node_modules/tshomo/dist/decode.js
  var object = (v) => v !== null && typeof v === "object" && !Array.isArray(v);
  var string = (v) => typeof v === "string";
  var number = (v) => typeof v === "number" && Number.isFinite(v);
  var boolean = (v) => typeof v === "boolean";
  var port = (v) => string(v) || number(v);
  function array(guard) {
    return (v) => Array.isArray(v) && v.every(guard);
  }
  function nullable(guard) {
    return (v) => v === null || guard(v);
  }
  function list(guard, label) {
    const decode = decoder(array(guard), label);
    return (v) => v === null ? [] : decode(v);
  }
  function optional(v, keys, guard) {
    return keys.every((key) => !Object.hasOwn(v, key) || guard(v[key]));
  }
  function fields(v, strings = [], numbers = [], booleans = []) {
    return object(v) && optional(v, strings, string) && optional(v, numbers, number) && optional(v, booleans, boolean);
  }
  function decoder(guard, label) {
    return (v) => {
      if (!guard(v)) {
        throw new DecodeError(`Invalid ${label}`);
      }
      return v;
    };
  }
  function parseJson(text) {
    try {
      return JSON.parse(text);
    } catch {
      throw new DecodeError("Invalid JSON response");
    }
  }
  var isJson = (v) => v === null || string(v) || boolean(v) || number(v) || (Array.isArray(v) ? v.every(isJson) : object(v) && Object.values(v).every(isJson));
  var json = decoder(isJson, "JSON value");
  var hello = decoder((v) => object(v) && string(v.hello), "hello");
  var version = decoder((v) => object(v) && string(v.version) && optional(v, ["premium", "meta"], boolean), "version");
  var maintenance = decoder((v) => object(v) && string(v.status), "maintenance result");
  var tun = (v) => fields(v, ["stack", "device"], [], ["enable", "auto-route", "auto-detect-interface"]);
  var config = decoder((v) => fields(v, ["mode", "log-level", "bind-address"], ["port", "socks-port", "redir-port", "tproxy-port", "mixed-port"], ["allow-lan", "ipv6"]) && optional(v, ["tun"], nullable(tun)) && optional(v, ["modes", "mode-list"], array(string)), "config");
  var history = (v) => object(v) && string(v.time) && number(v.delay);
  var isProxy = (v) => fields(v, ["now", "fixed", "testUrl", "icon"], [], ["udp", "xudp", "hidden", "selectable"]) && string(v.name) && string(v.type) && optional(v, ["all"], nullable(array(string))) && optional(v, ["extra"], object) && optional(v, ["history"], array(history));
  var proxy = decoder(isProxy, "proxy");
  var delay = decoder((v) => object(v) && number(v.delay), "delay");
  function map(guard) {
    return decoder((v) => object(v) && Object.values(v).every(guard), "resource map");
  }
  function envelope(key, decode) {
    return (v) => {
      if (!object(v) || !Object.hasOwn(v, key)) {
        throw new DecodeError(`Missing ${key}`);
      }
      return decode(v[key]);
    };
  }
  var subscription = (v) => fields(v, [], [
    "upload",
    "download",
    "total",
    "expire",
    "Upload",
    "Download",
    "Total",
    "Expire"
  ]);
  var providerBase = (v) => fields(v, ["type", "vehicleType", "updatedAt"]) && string(v.name);
  var isProxyProvider = (v) => providerBase(v) && optional(v, ["proxies"], array(isProxy)) && optional(v, ["subscriptionInfo"], subscription) && optional(v, ["testUrl", "expectedStatus"], string);
  var proxyProvider = decoder(isProxyProvider, "proxy provider");
  var isRuleProvider = (v) => providerBase(v) && optional(v, ["behavior", "format"], string) && optional(v, ["ruleCount"], number) && optional(v, ["payload"], array(string));
  var ruleProviders = envelope("providers", (v) => {
    if (array(string)(v)) {
      return Object.fromEntries(v.map((name) => [name, { name }]));
    }
    return map(isRuleProvider)(v);
  });
  var ruleExtra = (v) => fields(v, ["hitAt", "missAt"], ["hitCount", "missCount"], ["disabled"]);
  var isRule = (v) => fields(v, ["uuid"], ["size", "index"], ["disabled"]) && string(v.type) && string(v.payload) && string(v.proxy) && optional(v, ["extra"], ruleExtra);
  var rules = envelope("rules", list(isRule, "rules"));
  var metadata = (v) => fields(v, [
    "network",
    "type",
    "sourceIP",
    "destinationIP",
    "host",
    "dnsMode",
    "processPath"
  ]) && optional(v, ["sourcePort", "destinationPort", "inboundPort"], port);
  var connection = (v) => fields(v, ["rule", "rulePayload"]) && string(v.id) && metadata(v.metadata) && number(v.upload) && number(v.download) && port(v.start) && array(string)(v.chains);
  var connections = decoder((v) => object(v) && number(v.downloadTotal) && number(v.uploadTotal) && (v.connections === null || array(connection)(v.connections)) && optional(v, ["memory"], number), "connections snapshot");
  var traffic = decoder((v) => object(v) && number(v.up) && number(v.down), "traffic");
  var memory = decoder((v) => object(v) && number(v.inuse) && optional(v, ["oslimit"], number), "memory");
  var log = decoder((v) => object(v) && string(v.type) && string(v.payload), "log");
  var question = (v) => object(v) && string(v.Name) && number(v.Qtype) && number(v.Qclass);
  var rr = (v) => object(v) && string(v.name) && number(v.type) && string(v.data) && (number(v.TTL) || number(v.ttl)) && optional(v, ["TTL", "ttl"], number);
  var dns = decoder((v) => fields(v, ["server", "Server"], [], ["TC", "RD", "RA", "AD", "CD"]) && optional(v, ["Answer", "Authority", "Additional"], array(rr)) && optional(v, ["Status", "status"], number) && optional(v, ["Question", "question"], array(question)) && (number(v.Status) && array(question)(v.Question) || number(v.status) && (array(question)(v.Question) || array(question)(v.question))), "DNS response");

  // node_modules/tshomo/dist/extensions.js
  function extensionTransport(context) {
    function validate(request) {
      if (!["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"].includes(request.method)) {
        throw new InputError("Invalid extension request method");
      }
      if (request.json !== void 0 && request.bytes !== void 0) {
        throw new InputError("Extension requests cannot contain both JSON and bytes");
      }
      if (request.json !== void 0 && !isJson(request.json)) {
        throw new InputError("Extension request body must be valid JSON");
      }
      if (request.bytes !== void 0 && !(request.bytes instanceof Uint8Array)) {
        throw new InputError("Extension request bytes must be a Uint8Array");
      }
      if ((request.method === "GET" || request.method === "HEAD") && (request.json !== void 0 || request.bytes !== void 0)) {
        throw new InputError("GET and HEAD requests cannot contain a body");
      }
    }
    return Object.freeze({
      json(request, decoder2, options) {
        validate(request);
        return context.json(void 0, request, decoder2, options);
      },
      bytes(request, options) {
        validate(request);
        return context.request(void 0, request, (response, signal) => context.readBytes(response, signal), options);
      },
      void(request, options) {
        validate(request);
        return context.void(void 0, request, options);
      }
    });
  }

  // node_modules/tshomo/dist/transport/url.js
  function baseUrl(input) {
    let url;
    try {
      url = new URL(input);
    } catch {
      throw new InputError("baseUrl must be an absolute HTTP(S) URL");
    }
    if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || url.search || url.hash || input.includes("?") || input.includes("#")) {
      throw new InputError("baseUrl must use HTTP(S) without credentials, query or fragment");
    }
    if (!url.pathname.endsWith("/")) {
      url.pathname += "/";
    }
    return url;
  }
  function endpoint(base, segments, query = {}) {
    const encoded = segments.map((segment) => {
      if (typeof segment !== "string" || segment.length === 0 || segment === "." || segment === "..") {
        throw new InputError("Path segments must be nonempty and must not be dot segments");
      }
      try {
        return encodeURIComponent(segment);
      } catch {
        throw new InputError("Path segments must be valid Unicode strings");
      }
    });
    const url = new URL(base);
    url.pathname += encoded.join("/");
    for (const [key, value] of Object.entries(query)) {
      if (value !== void 0) {
        url.searchParams.set(key, String(value));
      }
    }
    return url;
  }
  function websocketUrl(url, secret) {
    const ws = new URL(url);
    ws.protocol = url.protocol === "https:" ? "wss:" : "ws:";
    if (secret) {
      ws.searchParams.set("token", secret);
    }
    return ws.href;
  }
  function duration(value, label, allowZero = true) {
    if (!Number.isFinite(value) || value < (allowZero ? 0 : 1) || !Number.isSafeInteger(value) || value > 2147483647) {
      throw new InputError(`${label} must be an integer ${allowZero ? ">= 0" : ">= 1"} and <= 2147483647`);
    }
    return value;
  }

  // node_modules/tshomo/dist/transport/http.js
  var HttpContext = class {
    base;
    secret;
    timeoutMs;
    fetcher;
    capabilities;
    disposed = false;
    cleanups = /* @__PURE__ */ new Set();
    constructor(options) {
      this.base = baseUrl(options.baseUrl);
      if (options.secret !== void 0 && typeof options.secret !== "string") {
        throw new InputError("secret must be a string");
      }
      try {
        if (options.secret) {
          new Headers({ Authorization: `Bearer ${options.secret}` });
        }
      } catch {
        throw new InputError("secret is not a valid header value");
      }
      this.secret = options.secret;
      this.timeoutMs = duration(options.requestTimeoutMs ?? 15e3, "requestTimeoutMs");
      this.fetcher = options.fetch ?? globalThis.fetch?.bind(globalThis);
      if (!this.fetcher) {
        throw new InputError("Fetch is unavailable; provide options.fetch");
      }
      this.capabilities = { ...options.capabilities };
    }
    check(capability) {
      if (this.disposed) {
        throw new ClientDisposedError();
      }
      if (capability !== void 0 && this.capabilities?.[capability] === "unsupported") {
        throw new UnsupportedCapabilityError(capability);
      }
    }
    own(cleanup) {
      if (this.disposed) {
        throw new ClientDisposedError();
      }
      this.cleanups.add(cleanup);
      return () => {
        this.cleanups.delete(cleanup);
      };
    }
    dispose() {
      if (this.disposed) {
        return;
      }
      this.disposed = true;
      for (const cleanup of [...this.cleanups]) {
        cleanup();
      }
      this.cleanups.clear();
    }
    redact(text) {
      if (this.secret || text.toLowerCase().includes("authorization") || text.toLowerCase().includes("token=")) {
        return "[server detail omitted]";
      }
      return text.slice(0, 512).split("\n").join(" ").split("\r").join(" ");
    }
    safeCause(value) {
      if (value instanceof Error) {
        return new Error(this.redact(value.message));
      }
      if (typeof value === "string") {
        return this.redact(value);
      }
      if (value === null || typeof value === "boolean" || typeof value === "number" && Number.isFinite(value)) {
        return value;
      }
      return void 0;
    }
    cancellation(reason) {
      return new AbortError(this.safeCause(reason));
    }
    safeResult(value) {
      return { status: value.status === "ok" ? "ok" : "unsuccessful" };
    }
    scope(signal, timeoutMs) {
      duration(timeoutMs, "timeoutMs");
      const controller = new AbortController();
      const startedAt = performance.now();
      let reason;
      const abort = (error) => {
        if (!controller.signal.aborted) {
          reason = error;
          controller.abort(error);
        }
      };
      const callerAbort = () => abort(this.cancellation(signal?.reason));
      const unown = this.own(() => abort(new ClientDisposedError()));
      signal?.addEventListener("abort", callerAbort, { once: true });
      if (signal?.aborted) {
        callerAbort();
      }
      const timer = timeoutMs ? setTimeout(() => abort(new TimeoutError()), timeoutMs) : void 0;
      return {
        signal: controller.signal,
        error: () => {
          if (timeoutMs && performance.now() - startedAt >= timeoutMs) {
            abort(new TimeoutError());
          }
          return reason;
        },
        finish: () => {
          unown();
          signal?.removeEventListener("abort", callerAbort);
          clearTimeout(timer);
        }
      };
    }
    async fetch(spec, signal) {
      if (signal.aborted) {
        throw new AbortError();
      }
      const headers = new Headers();
      if (this.secret) {
        headers.set("Authorization", `Bearer ${this.secret}`);
      }
      let body;
      if (spec.json !== void 0) {
        headers.set("Content-Type", "application/json");
        try {
          body = JSON.stringify(spec.json);
        } catch {
          throw new InputError("Request body is not JSON serializable");
        }
      } else if (spec.bytes !== void 0) {
        body = new Blob([new Uint8Array(spec.bytes)]);
      }
      const url = endpoint(this.base, spec.path, spec.query);
      let response;
      try {
        response = await this.fetcher(url, {
          method: spec.method,
          headers,
          ...body !== void 0 ? { body } : {},
          signal,
          credentials: "omit",
          redirect: "error"
        });
      } catch (error) {
        throw new NetworkError(this.safeCause(error));
      }
      if (!response.ok) {
        const detail = new TextDecoder().decode(await this.readBytes(response, signal, 8192));
        let message = detail;
        try {
          const data = parseJson(detail);
          if (object(data) && typeof data.message === "string") {
            message = data.message;
          }
        } catch {
        }
        throw new HttpError(spec.method, spec.path[0] ?? "root", response.status, this.redact(message));
      }
      return response;
    }
    async readBytes(response, signal, limit = Infinity) {
      if (!response.body) {
        return new Uint8Array();
      }
      const reader = response.body.getReader();
      const abort = () => {
        void reader.cancel().catch(() => {
        });
      };
      signal.addEventListener("abort", abort, { once: true });
      if (signal.aborted) {
        abort();
      }
      const chunks = [];
      let length = 0;
      try {
        while (length < limit) {
          let result;
          try {
            result = await reader.read();
          } catch (error) {
            throw new NetworkError(this.safeCause(error));
          }
          if (signal.aborted) {
            throw new AbortError();
          }
          if (result.done) {
            break;
          }
          const chunk = result.value.subarray(0, limit - length);
          chunks.push(chunk);
          length += chunk.byteLength;
        }
        const bytes = new Uint8Array(length);
        let offset = 0;
        for (const chunk of chunks) {
          bytes.set(chunk, offset);
          offset += chunk.byteLength;
        }
        return bytes;
      } finally {
        signal.removeEventListener("abort", abort);
        await reader.cancel().catch(() => {
        });
        reader.releaseLock();
      }
    }
    async request(capability, spec, decode, options = {}) {
      this.check(capability);
      endpoint(this.base, spec.path, spec.query);
      const scope = this.scope(options.signal, options.timeoutMs ?? this.timeoutMs);
      try {
        if (scope.signal.aborted) {
          throw scope.error();
        }
        const response = await this.fetch(spec, scope.signal);
        try {
          const value = await decode(response, scope.signal);
          if (scope.error()) {
            throw scope.error();
          }
          return value;
        } finally {
          await response.body?.cancel().catch(() => {
          });
        }
      } catch (error) {
        if (scope.error()) {
          throw scope.error();
        }
        if (error instanceof ClashError) {
          throw error;
        }
        throw new NetworkError(this.safeCause(error));
      } finally {
        scope.finish();
      }
    }
    json(capability, spec, decode, options) {
      return this.request(capability, spec, async (response, signal) => {
        const value = parseJson(new TextDecoder().decode(await this.readBytes(response, signal)));
        try {
          return decode(value);
        } catch (error) {
          if (error instanceof ClashError) {
            throw error;
          }
          throw new DecodeError("Response decoder failed");
        }
      }, options);
    }
    void(capability, spec, options) {
      return this.request(capability, spec, async (response) => {
        await response.body?.cancel();
      }, options);
    }
    operation(capability, spec, decode, options) {
      return this.json(capability, spec, (value) => {
        const result = decode(value);
        if (result.status !== "ok") {
          throw new OperationError(this.safeResult(result));
        }
        return result;
      }, options);
    }
  };

  // node_modules/tshomo/dist/resources/system.js
  function system(context) {
    return {
      hello: (options) => context.json("system.hello", { method: "GET", path: [] }, hello, options),
      getVersion: (options) => context.json("system.getVersion", { method: "GET", path: ["version"] }, version, options),
      gc: (options) => context.void("system.gc", { method: "PUT", path: ["gc"] }, options),
      restart: (options) => context.operation("system.restart", { method: "POST", path: ["restart"] }, maintenance, options),
      upgradeCore: (input = {}, options) => context.operation("system.upgradeCore", {
        method: "POST",
        path: ["upgrade"],
        query: { channel: input.channel, force: input.force }
      }, maintenance, options),
      upgradeUi: (options) => context.operation("system.upgradeUi", { method: "POST", path: ["upgrade", "ui"] }, maintenance, options),
      updateGeo: (options) => context.void("system.updateGeo", { method: "POST", path: ["upgrade", "geo"] }, options)
    };
  }

  // node_modules/tshomo/dist/resources/configs.js
  function configs(context) {
    return {
      get: (options) => context.json("configs.get", { method: "GET", path: ["configs"] }, config, options),
      patch(patch, options) {
        context.check("configs.patch");
        if (!object(patch)) {
          throw new InputError("Config patch must be an object");
        }
        const validate = (value) => value === void 0 || isJson(value) || object(value) && Object.values(value).every(validate);
        try {
          if (!validate(patch) || patch.tun === null) {
            throw new InputError();
          }
          config(patch);
        } catch {
          throw new InputError("Config patch must contain JSON values with valid known fields");
        }
        return context.void("configs.patch", { method: "PATCH", path: ["configs"], json: patch }, options);
      },
      load(input, loadOptions = {}, options) {
        context.check("configs.load");
        if (!object(input) || Object.hasOwn(input, "path") === Object.hasOwn(input, "payload")) {
          throw new InputError("Provide exactly one of path or payload");
        }
        const body = Object.hasOwn(input, "path") ? { path: input.path } : { payload: input.payload };
        if (!Object.values(body).every((value) => typeof value === "string")) {
          throw new InputError("Config path or payload must be a string");
        }
        return context.void("configs.load", {
          method: "PUT",
          path: ["configs"],
          query: { force: loadOptions.force },
          json: body
        }, options);
      },
      reload: (options) => context.void("configs.reload", {
        method: "PUT",
        path: ["configs"],
        query: { reload: true },
        json: { path: "", payload: "" }
      }, options),
      updateGeo: (options) => context.void("configs.updateGeo", { method: "POST", path: ["configs", "geo"] }, options)
    };
  }

  // node_modules/tshomo/dist/transport/delay.js
  var DEFAULT_DELAY_TIMEOUT_MS = 5e3;
  function delayRequest(context, capability, path, decode, input = {}, options = {}) {
    const delayTimeoutMs = input.timeoutMs ?? DEFAULT_DELAY_TIMEOUT_MS;
    duration(delayTimeoutMs, "delay timeoutMs");
    const timeoutMs = options.timeoutMs ?? (context.timeoutMs === 0 ? 0 : Math.max(context.timeoutMs, delayTimeoutMs + 1e3));
    return context.json(capability, {
      method: "GET",
      path,
      query: {
        url: input.url,
        timeout: delayTimeoutMs,
        expected: input.expectedStatus
      }
    }, decode, { ...options, timeoutMs });
  }

  // node_modules/tshomo/dist/resources/proxies.js
  function proxies(context) {
    return {
      list: (options) => context.json("proxies.list", { method: "GET", path: ["proxies"] }, envelope("proxies", map(isProxy)), options),
      get: (name, options) => context.json("proxies.get", { method: "GET", path: ["proxies", name] }, proxy, options),
      select: (group, name, options) => context.void("proxies.select", { method: "PUT", path: ["proxies", group], json: { name } }, options),
      clearFixed: (group, options) => context.void("proxies.clearFixed", { method: "DELETE", path: ["proxies", group] }, options),
      delay: (name, input, options) => delayRequest(context, "proxies.delay", ["proxies", name, "delay"], delay, input, options)
    };
  }
  function groups(context) {
    return {
      list: (options) => context.json("groups.list", { method: "GET", path: ["group"] }, envelope("proxies", list(isProxy, "groups")), options),
      get: (name, options) => context.json("groups.get", { method: "GET", path: ["group", name] }, proxy, options),
      delay: (name, input, options) => delayRequest(context, "groups.delay", ["group", name, "delay"], map(number), input, options)
    };
  }

  // node_modules/tshomo/dist/resources/providers.js
  function proxyProviders(context) {
    const root = ["providers", "proxies"];
    return {
      list: (options) => context.json("proxyProviders.list", { method: "GET", path: root }, envelope("providers", map(isProxyProvider)), options),
      get: (name, options) => context.json("proxyProviders.get", { method: "GET", path: [...root, name] }, proxyProvider, options),
      update: (name, options) => context.void("proxyProviders.update", { method: "PUT", path: [...root, name] }, options),
      healthCheck: (name, options) => context.void("proxyProviders.healthCheck", { method: "GET", path: [...root, name, "healthcheck"] }, options),
      getProxy: (provider, name, options) => context.json("proxyProviders.getProxy", { method: "GET", path: [...root, provider, name] }, proxy, options),
      delayProxy: (provider, name, input, options) => delayRequest(context, "proxyProviders.delayProxy", [...root, provider, name, "healthcheck"], delay, input, options)
    };
  }
  function ruleProviders2(context) {
    return {
      list: (options) => context.json("ruleProviders.list", { method: "GET", path: ["providers", "rules"] }, ruleProviders, options),
      update: (name, options) => context.void("ruleProviders.update", { method: "PUT", path: ["providers", "rules", name] }, options)
    };
  }

  // node_modules/tshomo/dist/resources/rules.js
  function rules2(context) {
    return {
      list: (options) => context.json("rules.list", { method: "GET", path: ["rules"] }, rules, options),
      disableByIndex(changes, options) {
        context.check("rules.disableByIndex");
        for (const [key, value] of Object.entries(changes)) {
          const index = Number(key);
          if (!Number.isSafeInteger(index) || index < 0 || String(index) !== key || typeof value !== "boolean") {
            throw new InputError("Rule changes require nonnegative integer indices and boolean values");
          }
        }
        return context.void("rules.disableByIndex", { method: "PATCH", path: ["rules", "disable"], json: changes }, options);
      }
    };
  }

  // node_modules/tshomo/dist/transport/websocket.js
  function readWebSocket(factory, url, feed, options) {
    return new Promise((resolve, reject) => {
      if (options.signal.aborted) {
        reject(new AbortError());
        return;
      }
      if (!factory && typeof globalThis.WebSocket === "undefined") {
        reject(new UnsupportedTransportError());
        return;
      }
      let socket;
      try {
        socket = factory ? factory(url) : new globalThis.WebSocket(url);
      } catch {
        reject(new StreamError());
        return;
      }
      let done = false;
      let opened = false;
      let connectTimer;
      let idleTimer;
      function cleanup() {
        clearTimeout(connectTimer);
        clearTimeout(idleTimer);
        options.signal.removeEventListener("abort", abort);
        socket.removeEventListener("open", open);
        socket.removeEventListener("message", message);
        socket.removeEventListener("error", error);
        socket.removeEventListener("close", close);
        try {
          socket.close();
        } catch {
        }
      }
      function finish(error2) {
        if (done) {
          return;
        }
        done = true;
        cleanup();
        if (error2) {
          reject(error2);
        } else {
          resolve();
        }
      }
      function idle() {
        clearTimeout(idleTimer);
        if (options.idleTimeoutMs) {
          idleTimer = setTimeout(() => finish(new TimeoutError()), options.idleTimeoutMs);
        }
      }
      function open() {
        if (done || opened) {
          return;
        }
        opened = true;
        clearTimeout(connectTimer);
        idle();
        feed.open();
      }
      function message(event) {
        if (done) {
          return;
        }
        if (!opened) {
          finish(new StreamError("Message received before socket opened", false));
          return;
        }
        if (typeof event.data !== "string") {
          finish(new DecodeError("Binary WebSocket messages are unsupported"));
          return;
        }
        if (new TextEncoder().encode(event.data).byteLength > options.maxFrameBytes) {
          finish(new StreamError("Frame size limit exceeded", false));
          return;
        }
        idle();
        try {
          feed.message(event.data);
        } catch (error2) {
          finish(error2);
        }
      }
      function error() {
        finish(new StreamError());
      }
      function close(event) {
        finish(event.code === 1e3 ? void 0 : new StreamError());
      }
      function abort() {
        finish(new AbortError());
      }
      socket.addEventListener("open", open);
      socket.addEventListener("message", message);
      socket.addEventListener("error", error);
      socket.addEventListener("close", close);
      options.signal.addEventListener("abort", abort, { once: true });
      if (options.connectTimeoutMs) {
        connectTimer = setTimeout(() => finish(new TimeoutError()), options.connectTimeoutMs);
      }
      if (options.signal.aborted) {
        abort();
      } else if (socket.readyState === 1) {
        open();
      } else if (socket.readyState > 1) {
        finish(new StreamError());
      }
    });
  }

  // node_modules/tshomo/dist/transport/json-stream.js
  async function readJsonStream(context, spec, feed, options) {
    const controller = new AbortController();
    let timedOut = false;
    let timer;
    let reader;
    const abort = () => {
      controller.abort();
      void reader?.cancel().catch(() => {
      });
    };
    const timeout = () => {
      timedOut = true;
      abort();
    };
    const idle = () => {
      clearTimeout(timer);
      timer = options.idleTimeoutMs ? setTimeout(timeout, options.idleTimeoutMs) : void 0;
    };
    options.signal.addEventListener("abort", abort, { once: true });
    if (options.signal.aborted) {
      abort();
    }
    if (options.connectTimeoutMs) {
      timer = setTimeout(timeout, options.connectTimeoutMs);
    }
    try {
      const response = await context.fetch(spec, controller.signal);
      clearTimeout(timer);
      if (controller.signal.aborted) {
        throw new AbortError();
      }
      if (!response.body) {
        throw new DecodeError("Missing stream body");
      }
      reader = response.body.getReader();
      idle();
      feed.open();
      let parts = [];
      let size = 0;
      const append = (bytes) => {
        size += bytes.byteLength;
        if (size > options.maxFrameBytes) {
          throw new StreamError("Frame size limit exceeded", false);
        }
        if (bytes.byteLength) {
          parts.push(bytes);
        }
      };
      const emit = () => {
        const frame = new Uint8Array(size);
        let offset = 0;
        for (const part of parts) {
          frame.set(part, offset);
          offset += part.byteLength;
        }
        let text;
        try {
          text = new TextDecoder("utf-8", { fatal: true }).decode(frame);
        } catch {
          throw new DecodeError("Invalid UTF-8 stream frame");
        }
        parts = [];
        size = 0;
        if (text.trim()) {
          feed.message(text);
        }
      };
      while (!controller.signal.aborted) {
        let result;
        try {
          result = await reader.read();
        } catch (error) {
          throw new NetworkError(context.safeCause(error));
        }
        if (controller.signal.aborted) {
          throw new AbortError();
        }
        const { value, done } = result;
        if (done) {
          if (size) {
            emit();
          }
          break;
        }
        idle();
        let start = 0;
        for (let index = 0; index < value.byteLength; index++) {
          if (value[index] === 10) {
            append(value.subarray(start, index));
            emit();
            start = index + 1;
          }
        }
        append(value.subarray(start));
      }
      if (controller.signal.aborted) {
        throw new AbortError();
      }
    } catch (error) {
      if (timedOut) {
        throw new TimeoutError();
      }
      if (options.signal.aborted) {
        throw new AbortError();
      }
      throw error;
    } finally {
      clearTimeout(timer);
      options.signal.removeEventListener("abort", abort);
      controller.abort();
      if (reader) {
        await reader.cancel().catch(() => {
        });
        reader.releaseLock();
      }
    }
  }

  // node_modules/tshomo/dist/transport/stream.js
  function retryable(error) {
    return error instanceof StreamError && error.recoverable || error instanceof NetworkError || error instanceof TimeoutError || error instanceof HttpError && error.status >= 500;
  }
  function watch(context, factory, capability, path, query, decode, logs2, options = {}) {
    context.check(capability);
    options = {
      ...options,
      ...options.reconnect ? { reconnect: { ...options.reconnect } } : {}
    };
    const url = endpoint(context.base, path, query);
    const connectTimeoutMs = duration(options.connectTimeoutMs ?? 1e4, "connectTimeoutMs");
    const idleTimeoutMs = duration(options.idleTimeoutMs ?? 0, "idleTimeoutMs");
    const maxFrameBytes = duration(options.maxFrameBytes ?? 16 * 1024 * 1024, "maxFrameBytes", false);
    if (options.transport !== void 0 && options.transport !== "websocket" && options.transport !== "http-json") {
      throw new InputError("Invalid stream transport");
    }
    const reconnect = options.reconnect;
    if (reconnect) {
      duration(reconnect.maxAttempts, "maxAttempts");
      duration(reconnect.initialDelayMs ?? 500, "initialDelayMs");
      duration(reconnect.maxDelayMs ?? 3e4, "maxDelayMs");
    }
    let state = "idle";
    let terminal = false;
    let claimed = false;
    let started = false;
    let failure;
    let queue = [];
    let pending;
    let connection2;
    let retryTimer;
    let wakeRetry;
    let openedAt;
    const change = (next) => {
      state = next;
      try {
        options.onStateChange?.(next);
      } catch {
      }
    };
    const unown = context.own(() => stop());
    function stop(error, drain = false) {
      if (terminal) {
        if (!drain) {
          queue = [];
        }
        return;
      }
      terminal = true;
      failure = error;
      connection2?.abort();
      clearTimeout(retryTimer);
      wakeRetry?.();
      options.signal?.removeEventListener("abort", abort);
      unown();
      if (!drain || error) {
        queue = [];
      }
      change(error ? "failed" : "closed");
      if (pending) {
        const waiter = pending;
        pending = void 0;
        if (error) {
          waiter.reject(error);
        } else {
          waiter.resolve({ done: true, value: void 0 });
        }
      }
    }
    function abort() {
      stop(context.cancellation(options.signal?.reason));
    }
    options.signal?.addEventListener("abort", abort, { once: true });
    if (options.signal?.aborted) {
      abort();
    }
    function push(value) {
      if (terminal) {
        return;
      }
      if (pending) {
        const waiter = pending;
        pending = void 0;
        waiter.resolve({ done: false, value });
        return;
      }
      if (!logs2) {
        queue = [value];
      } else if (queue.length >= 256) {
        stop(new BufferOverflowError());
      } else {
        queue.push(value);
      }
    }
    async function run() {
      let attempts = 0;
      while (!terminal) {
        openedAt = void 0;
        connection2 = new AbortController();
        change(attempts ? "reconnecting" : "connecting");
        if (terminal) {
          return;
        }
        try {
          const feed = {
            open: () => {
              if (!terminal) {
                openedAt = performance.now();
                change("open");
              }
            },
            message: (text) => {
              if (terminal) {
                return;
              }
              push(decode(parseJson(text)));
            }
          };
          const connectionOptions = {
            signal: connection2.signal,
            connectTimeoutMs,
            idleTimeoutMs,
            maxFrameBytes
          };
          if (options.transport === "http-json") {
            await readJsonStream(context, { method: "GET", path, query }, feed, connectionOptions);
          } else {
            await readWebSocket(factory, websocketUrl(url, context.secret), feed, connectionOptions);
          }
          if (!terminal) {
            stop(void 0, true);
          }
        } catch (error) {
          if (terminal) {
            return;
          }
          if (openedAt !== void 0 && performance.now() - openedAt >= 3e4) {
            attempts = 0;
          }
          if (!reconnect || !retryable(error) || attempts >= reconnect.maxAttempts) {
            stop(error instanceof ClashError ? error : new DecodeError("Stream decoder failed"));
            return;
          }
          const delay2 = Math.min(reconnect.maxDelayMs ?? 3e4, (reconnect.initialDelayMs ?? 500) * 2 ** Math.min(attempts, 30));
          attempts++;
          change("reconnecting");
          if (terminal) {
            return;
          }
          await new Promise((resolve) => {
            wakeRetry = resolve;
            retryTimer = setTimeout(resolve, Math.min(2147483647, Math.min(reconnect.maxDelayMs ?? 3e4, delay2 * (0.8 + Math.random() * 0.4))));
          });
          wakeRetry = void 0;
          retryTimer = void 0;
        }
      }
    }
    return {
      get state() {
        return state;
      },
      close: () => stop(),
      [Symbol.asyncIterator]() {
        if (claimed) {
          throw new InputError("Streams support only one consumer");
        }
        claimed = true;
        return {
          next() {
            if (pending) {
              return Promise.reject(new InputError("Concurrent next() calls are unsupported"));
            }
            if (queue.length) {
              const value = queue.shift();
              if (value !== void 0) {
                return Promise.resolve({ done: false, value });
              }
            }
            if (terminal) {
              return failure ? Promise.reject(failure) : Promise.resolve({ done: true, value: void 0 });
            }
            const next = new Promise((resolve, reject) => {
              pending = { resolve, reject };
            });
            if (!started) {
              started = true;
              void run().catch(() => stop(new StreamError()));
            }
            return next;
          },
          return() {
            stop();
            return Promise.resolve({ done: true, value: void 0 });
          },
          throw(error) {
            stop();
            return Promise.reject(error);
          }
        };
      }
    };
  }

  // node_modules/tshomo/dist/resources/connections.js
  function connections2(context, factory) {
    return {
      snapshot: (options) => context.json("connections.snapshot", { method: "GET", path: ["connections"] }, connections, options),
      close: (id, options) => context.void("connections.close", { method: "DELETE", path: ["connections", id] }, options),
      closeAll: (options) => context.void("connections.closeAll", { method: "DELETE", path: ["connections"] }, options),
      watch(options = {}) {
        if (options.transport !== void 0 && options.transport !== "websocket") {
          throw new InputError("Connections only support WebSocket subscriptions");
        }
        if (options.intervalMs !== void 0) {
          duration(options.intervalMs, "intervalMs", false);
        }
        return watch(context, factory, "connections.watch", ["connections"], { interval: options.intervalMs }, connections, false, options);
      }
    };
  }

  // node_modules/tshomo/dist/resources/metrics.js
  async function sample(context, factory, capability, path, decode, options = {}) {
    context.check(capability);
    const scope = context.scope(options.signal, options.timeoutMs ?? (context.timeoutMs || 15e3));
    let stream;
    try {
      stream = watch(context, factory, capability, path, {}, decode, false, {
        ...options,
        signal: scope.signal
      });
      const result = await stream[Symbol.asyncIterator]().next();
      if (scope.error()) {
        throw scope.error();
      }
      if (result.done) {
        throw new DecodeError("Stream ended before the first sample");
      }
      return result.value;
    } catch (error) {
      throw scope.error() ?? error;
    } finally {
      stream?.close();
      scope.finish();
    }
  }
  function metrics(context, factory) {
    return {
      watchTraffic: (options) => watch(context, factory, "metrics.watchTraffic", ["traffic"], {}, traffic, false, options),
      watchMemory: (options) => watch(context, factory, "metrics.watchMemory", ["memory"], {}, memory, false, options),
      trafficSample: (options) => sample(context, factory, "metrics.trafficSample", ["traffic"], traffic, options),
      memorySample: (options) => sample(context, factory, "metrics.memorySample", ["memory"], memory, options)
    };
  }

  // node_modules/tshomo/dist/resources/logs.js
  function logs(context, factory) {
    return {
      watch: (options = {}) => watch(context, factory, "logs.watch", ["logs"], { level: options.level }, log, true, options)
    };
  }

  // node_modules/tshomo/dist/resources/dns.js
  function dns2(context) {
    return {
      query(input, options) {
        context.check("dns.query");
        if (typeof input.name !== "string" || !input.name.trim()) {
          throw new InputError("DNS name must not be empty");
        }
        return context.json("dns.query", { method: "GET", path: ["dns", "query"], query: input }, dns, options);
      }
    };
  }
  function cache(context) {
    return {
      flushFakeIp: (options) => context.void("cache.flushFakeIp", { method: "POST", path: ["cache", "fakeip", "flush"] }, options),
      flushDns: (options) => context.void("cache.flushDns", { method: "POST", path: ["cache", "dns", "flush"] }, options)
    };
  }

  // node_modules/tshomo/dist/resources/storage.js
  function storage(context) {
    function getJson(key, decoder2, options) {
      return context.json("storage.getJson", { method: "GET", path: ["storage", key] }, (value) => decoder2 ? decoder2(value) : json(value), options);
    }
    return {
      getBytes: (key, options) => context.request("storage.getBytes", { method: "GET", path: ["storage", key] }, (response, signal) => context.readBytes(response, signal), options),
      /** Sends raw bytes without Content-Type. Known kernels require valid JSON. */
      putBytes: (key, bytes, options) => context.void("storage.putBytes", { method: "PUT", path: ["storage", key], bytes }, options),
      delete: (key, options) => context.void("storage.delete", { method: "DELETE", path: ["storage", key] }, options),
      getJson,
      putJson(key, value, options) {
        context.check("storage.putJson");
        if (!isJson(value)) {
          throw new InputError("Storage value must be valid JSON");
        }
        return context.void("storage.putJson", { method: "PUT", path: ["storage", key], json: value }, options);
      }
    };
  }

  // node_modules/tshomo/dist/client.js
  function createClashClient(options) {
    const context = new HttpContext(options);
    const transport = extensionTransport(context);
    const factory = options.webSocketFactory;
    return {
      system: system(context),
      configs: configs(context),
      proxies: proxies(context),
      groups: groups(context),
      proxyProviders: proxyProviders(context),
      ruleProviders: ruleProviders2(context),
      rules: rules2(context),
      connections: connections2(context, factory),
      metrics: metrics(context, factory),
      logs: logs(context, factory),
      dns: dns2(context),
      cache: cache(context),
      storage: storage(context),
      extend(factory2) {
        context.check();
        return factory2(transport);
      },
      dispose: () => context.dispose()
    };
  }

  // node_modules/tshomo/dist/extensions/refind.js
  function isRefindRule(rule) {
    return typeof rule.uuid === "string" && rule.uuid.length > 0;
  }
  function refind(transport) {
    return {
      rules: {
        /** Toggles the current state once. Non-idempotent; do not automatically retry. */
        toggleByUuid(uuid, options) {
          if (typeof uuid !== "string" || uuid.length === 0) {
            throw new InputError("Rule UUID must be a nonempty string");
          }
          return transport.void({ method: "PUT", path: ["rules", uuid] }, options);
        }
      }
    };
  }

  // src/clash.ts
  function createClashSession(url, secret, logDebug) {
    const client = createClashClient({
      baseUrl: url,
      secret,
      requestTimeoutMs: 1e4,
      fetch: loggedFetch(logDebug)
    });
    return new ClashSession(client, logDebug);
  }
  var ClashSession = class {
    constructor(client, logDebug) {
      this.client = client;
      this.logDebug = logDebug;
      this.extension = client.extend(refind);
    }
    client;
    logDebug;
    extension;
    closed = false;
    requests = /* @__PURE__ */ new Map();
    close() {
      this.logDebug("Clash session closed");
      this.closed = true;
      this.client.dispose();
    }
    cancelRequest(id) {
      this.requests.get(id)?.abort();
    }
    async request(operation, input, id) {
      const started = performance.now();
      this.logDebug(`Clash request ${id}: ${operation}`);
      const controller = new AbortController();
      this.requests.set(id, controller);
      try {
        const args = JSON.parse(input);
        const value = await this.execute(operation, args, { signal: controller.signal });
        this.logDebug(`Clash request ${id}: ${operation} completed in ${Math.round(performance.now() - started)}ms`);
        return JSON.stringify({ value: value ?? null });
      } catch (error) {
        this.logDebug(`Clash request ${id}: ${operation} failed`);
        return JSON.stringify({ error: describeError(error) });
      } finally {
        this.requests.delete(id);
      }
    }
    watch(kind, interval, receive) {
      const options = {
        reconnect: false,
        connectTimeoutMs: 1e4,
        onStateChange: (state) => this.logDebug(`Clash stream ${kind}: ${state}`)
      };
      this.logDebug(`Starting Clash stream ${kind}, interval=${interval}ms`);
      let stream;
      switch (kind) {
        case "traffic":
          stream = this.client.metrics.watchTraffic(options);
          break;
        case "connections":
          stream = this.client.connections.watch({ ...options, intervalMs: interval });
          break;
        case "logs":
          stream = this.client.logs.watch({ ...options, level: "debug" });
          break;
        case "memory":
          stream = this.client.metrics.watchMemory(options);
          break;
        default:
          throw new Error("Unknown Clash subscription");
      }
      let stopped = false;
      void (async () => {
        try {
          for await (const value of stream) {
            if (!stopped && !this.closed) {
              receive(JSON.stringify({ value }));
            }
          }
          if (!stopped && !this.closed) {
            receive(JSON.stringify({ error: { kind: "stream", message: "Stream ended" } }));
          }
        } catch (error) {
          if (!stopped && !this.closed) {
            receive(JSON.stringify({ error: describeError(error) }));
          }
        }
      })();
      return { close() {
        stopped = true;
        stream.close();
      } };
    }
    async execute(operation, args, options) {
      switch (operation) {
        case "version":
          return this.client.system.getVersion(options);
        case "proxies":
          return this.client.proxies.list(options);
        case "select":
          return this.client.proxies.select(args.group, args.name, options);
        case "delay":
          return this.client.proxies.delay(args.name, void 0, options);
        case "closeConnection":
          return this.client.connections.close(args.id, options);
        case "closeConnections":
          return this.client.connections.closeAll(options);
        case "config":
          return this.client.configs.get(options);
        case "setMode":
          return this.client.configs.patch({ mode: args.mode }, options);
        case "rules":
          return (await this.client.rules.list(options)).map((rule) => ({ ...rule, toggleable: isRefindRule(rule) }));
        case "toggleRule":
          return this.extension.rules.toggleByUuid(args.uuid, options);
        case "proxyProviders":
          return Object.entries(await this.client.proxyProviders.list(options)).map(([name, provider]) => ({
            ...provider,
            name
          }));
        case "ruleProviders":
          return Object.entries(await this.client.ruleProviders.list(options)).map(([name, provider]) => ({ ...provider, name }));
        case "updateProxyProvider":
          return this.client.proxyProviders.update(args.name, options);
        case "healthCheck":
          return this.client.proxyProviders.healthCheck(args.name, options);
        case "providerDelay":
          return this.client.proxyProviders.delayProxy(args.provider, args.name, void 0, options);
        case "updateRuleProvider":
          return this.client.ruleProviders.update(args.name, options);
        case "dns":
          return this.client.dns.query({ name: args.name, type: args.type }, options);
        default:
          throw new Error("Unknown Clash operation");
      }
    }
  };
  function describeError(error) {
    return {
      kind: error instanceof ClashError ? error.kind : "unknown",
      status: error instanceof HttpError ? error.status : null,
      message: error instanceof Error ? error.message : "Clash request failed"
    };
  }
  function loggedFetch(logDebug) {
    return async (input, init) => {
      const url = new URL(input instanceof Request ? input.url : String(input));
      const method = init?.method ?? (input instanceof Request ? input.method : "GET");
      const started = performance.now();
      const response = await globalThis.fetch(input, init);
      logDebug(`Clash HTTP ${method} ${url.pathname}: ${response.status}, ${Math.round(performance.now() - started)}ms`);
      return response;
    };
  }

  // src/remote-server-storage.ts
  var SERVER_STORE = "servers";
  var ORDER_INDEX = "userOrder";
  function createRemoteServerStorage(databaseName) {
    return new RemoteServerStorage(databaseName);
  }
  var RemoteServerStorage = class {
    constructor(databaseName) {
      this.databaseName = databaseName;
      try {
        this.channel = new BroadcastChannel(`${databaseName}:servers`);
        this.channel.onmessage = () => this.notify();
      } catch {
      }
      window.addEventListener("focus", this.notify);
      window.addEventListener("pageshow", this.notify);
      document.addEventListener("visibilitychange", this.onVisibilityChange);
    }
    databaseName;
    database;
    listeners = /* @__PURE__ */ new Set();
    channel;
    closed = false;
    async list() {
      const servers = await this.transaction("readonly", (store, result) => {
        const request = store.getAll();
        request.onsuccess = () => result(request.result);
      });
      servers.sort((left, right) => left.userOrder - right.userOrder || left.id - right.id);
      return JSON.stringify(servers);
    }
    async save(value) {
      const server = {
        id: 0,
        userOrder: 0,
        name: "",
        url: "",
        secret: "",
        ...JSON.parse(value)
      };
      if (!Number.isSafeInteger(server.id) || server.id < 0 || !Number.isSafeInteger(server.userOrder) || server.userOrder < 0) {
        throw new Error("Invalid server ID or order");
      }
      const saved = await this.transaction("readwrite", (store, result) => {
        const insert = () => {
          const request = store.index(ORDER_INDEX).openCursor(null, "prev");
          request.onsuccess = () => {
            const userOrder = server.userOrder || (request.result?.value.userOrder ?? 0) + 1;
            const record = { ...server, userOrder };
            if (server.id === 0) {
              delete record.id;
            }
            const added = store.add(record);
            added.onsuccess = () => result({ ...server, userOrder, id: added.result });
          };
        };
        if (server.id === 0) {
          insert();
        } else {
          const request = store.get(server.id);
          request.onsuccess = () => {
            if (request.result) {
              const updated = store.put(server);
              updated.onsuccess = () => result(server);
            } else {
              insert();
            }
          };
        }
      });
      this.changed();
      return JSON.stringify(saved);
    }
    async delete(id) {
      const key = Number(id);
      if (!Number.isSafeInteger(key) || key <= 0) throw new Error("Invalid server ID");
      await this.transaction("readwrite", (store, result) => {
        const request = store.delete(key);
        request.onsuccess = () => result();
      });
      this.changed();
    }
    subscribe(listener) {
      this.listeners.add(listener);
      return { close: () => {
        this.listeners.delete(listener);
      } };
    }
    close() {
      this.closed = true;
      void this.database?.then((database) => database.close(), () => {
      });
      this.channel?.close();
      this.listeners.clear();
      window.removeEventListener("focus", this.notify);
      window.removeEventListener("pageshow", this.notify);
      document.removeEventListener("visibilitychange", this.onVisibilityChange);
    }
    onVisibilityChange = () => {
      if (document.visibilityState === "visible") this.notify();
    };
    notify = () => {
      for (const listener of this.listeners) {
        try {
          listener();
        } catch (error) {
          console.error("Server storage observer failed", error);
        }
      }
    };
    changed() {
      this.notify();
      try {
        this.channel?.postMessage(null);
      } catch {
      }
    }
    open() {
      if (this.closed) return Promise.reject(new Error("Server storage is closed"));
      return this.database ??= new Promise((resolve, reject) => {
        const request = indexedDB.open(this.databaseName, 1);
        request.onupgradeneeded = () => {
          const store = request.result.createObjectStore(SERVER_STORE, {
            keyPath: "id",
            autoIncrement: true
          });
          store.createIndex(ORDER_INDEX, ORDER_INDEX);
        };
        request.onsuccess = () => {
          const database = request.result;
          database.onversionchange = () => {
            database.close();
            this.database = void 0;
          };
          database.onclose = () => {
            this.database = void 0;
          };
          if (this.closed) {
            database.close();
            reject(new Error("Server storage is closed"));
          } else {
            resolve(database);
          }
        };
        request.onerror = () => reject(request.error);
      }).catch((error) => {
        this.database = void 0;
        throw error;
      });
    }
    async transaction(mode, execute) {
      const database = await this.open();
      return new Promise((resolve, reject) => {
        const transaction = database.transaction(SERVER_STORE, mode);
        let result;
        transaction.oncomplete = () => resolve(result);
        transaction.onabort = () => reject(transaction.error ?? new Error("Server storage transaction aborted"));
        try {
          execute(transaction.objectStore(SERVER_STORE), (value) => {
            result = value;
          });
        } catch (error) {
          transaction.abort();
          reject(error);
        }
      });
    }
  };

  // src/index.ts
  function saveFile(name, base64) {
    const bytes = Uint8Array.from(atob(base64), (value) => value.charCodeAt(0));
    const url = URL.createObjectURL(new Blob([bytes]));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = name;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1e3);
  }
  async function qrPng(value) {
    const { toDataURL } = await Promise.resolve().then(() => __toESM(require_browser(), 1));
    return toDataURL(value, { width: 512, margin: 2 });
  }
  return __toCommonJS(index_exports);
})();
